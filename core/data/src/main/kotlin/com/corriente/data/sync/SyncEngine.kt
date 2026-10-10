package com.corriente.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.corriente.data.backup.BackupProblem
import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/** После применения чужих изменений БД не прошла проверку целостности — всё применение откатано. */
class SyncIntegrityException(val problems: List<BackupProblem>) :
    IllegalStateException("Sync would break data integrity: ${problems.take(5)}")

/**
 * Движок синхронизации (docs/SYNC_DESIGN.md §2–§4): выгружает локальные изменения из `sync_outbox` в свой файл папки,
 * применяет чужие файлы по LWW (версия строки — [Hlc]) с правилами §4, проверяет целостность.
 *
 * Применение — одной транзакцией: либо всё, либо ничего (как восстановление бэкапа). Пока применяются чужие
 * изменения, `sync_state.applying = 1` — триггеры не возвращают их в outbox.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val folder: SyncFolder,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Result(
        val exported: Int = 0,
        val applied: Int = 0,
        /** Ждут строк, на которые ссылаются (придут следующим обменом). */
        val deferred: Int = 0,
        /** Отклонены правилами §4 (удаление используемой строки, смена валюты счёта с операциями) — переразосланы. */
        val refused: Int = 0,
        /** Устройства, чьи файлы новее схемы этого приложения: нужно обновить приложение здесь. */
        val newerSchemaDevices: List<String> = emptyList(),
    )

    private val dao = db.syncDao()
    private val settings = db.appSettingDao()
    private val sql get() = db.openHelper.writableDatabase

    suspend fun isEnabled(): Boolean = dao.state()?.enabled == true

    suspend fun deviceId(): String? = settings.get(KEY_DEVICE_ID)

    suspend fun isPrimary(): Boolean = settings.get(KEY_PRIMARY) == "true"

    /**
     * Включает синхронизацию. [baseline] — выложить все текущие строки (устройство, с которого синхронизация начинается):
     * иначе данные, появившиеся до включения, никогда не попали бы в журнал (§6). [primary] — основное устройство (импорт
     * Monefy разрешён только на нём, §10).
     */
    suspend fun enable(name: String, primary: Boolean, baseline: Boolean) = db.withTransaction {
        if (settings.get(KEY_DEVICE_ID) == null) set(KEY_DEVICE_ID, UUID.randomUUID().toString())
        set(KEY_DEVICE_NAME, name)
        set(KEY_PRIMARY, primary.toString())
        dao.setState(SyncStateEntity(enabled = true))
        if (baseline) enqueueAll()
    }

    suspend fun disable() = dao.setState(SyncStateEntity(enabled = false))

    /** Карточки всех устройств из папки (включая это) — для экрана «Синхронизация». */
    fun devices(): List<DeviceInfo> =
        folder.list(SyncPaths.DEVICES).filter { it.endsWith(".json") && !it.startsWith(".") }.mapNotNull { name ->
            folder.read("${SyncPaths.DEVICES}/$name")
                ?.let { runCatching { syncJson.decodeFromString(DeviceInfo.serializer(), it.decodeToString()) }.getOrNull() }
        }

    /**
     * Подключение устройства «с заменой данных» (§6): всё своё удаляется (кроме валют и настроек), дальше данные придут из
     * папки. Перед вызовом — копия БД (`PreMigrationBackup`). Синхронизация в этот момент должна быть выключена, иначе
     * удаления разошлись бы по устройствам.
     */
    suspend fun wipeLocalData() = db.withTransaction {
        check(!isEnabled()) { "disable sync before wiping local data" }
        listOf(
            "DELETE FROM txn", "DELETE FROM budget", "DELETE FROM recurrence", "DELETE FROM import_alias",
            "DELETE FROM category_redirect", "DELETE FROM category WHERE parent_id IS NOT NULL", "DELETE FROM category",
            "DELETE FROM account", "DELETE FROM import_batch", "DELETE FROM sync_row_meta", "DELETE FROM sync_deferred",
            "DELETE FROM sync_outbox",
        ).forEach(sql::execSQL)
    }

    /** Есть ли на устройстве свои данные (счета/операции) — от этого зависит вопрос при подключении (§6). */
    suspend fun hasLocalData(): Boolean =
        sql.query("SELECT (SELECT COUNT(*) FROM account) + (SELECT COUNT(*) FROM txn)").use { it.moveToFirst() && it.getLong(0) > 0 }

    /** Один обмен: выгрузить своё, применить чужое. */
    suspend fun sync(): Result {
        check(isEnabled()) { "sync is not enabled" }
        val exported = db.withTransaction { export() }
        return db.withTransaction {
            // Правка, сделанная между двумя транзакциями, должна получить версию до сравнения с чужими.
            val again = export()
            import().copy(exported = exported + again)
        }
    }

    // ---------------------------------------------------------------- выгрузка

    private suspend fun export(): Int {
        val pending = dao.outbox()
        if (pending.isEmpty()) return 0
        val me = requireNotNull(deviceId())
        var clock = loadClock(me)
        val changes = pending.map { item ->
            clock = clock.tick(now())
            val hlc = clock.encode()
            val row = readRow(item.tableName, item.pk)
            dao.setMeta(SyncRowMetaEntity(item.tableName, item.pk, hlc, deleted = row == null))
            RowChange(item.tableName, item.pk, hlc, deleted = row == null, row = row)
        }
        val seq = (settings.get(KEY_SEQ)?.toLongOrNull() ?: 0L) + 1
        val file = ChangeFile(schemaVersion = AppDatabase.SCHEMA_VERSION, deviceId = me, seq = seq, changes = changes)
        folder.write(SyncPaths.changeFile(me, seq), syncJson.encodeToString(ChangeFile.serializer(), file).toByteArray())
        set(KEY_SEQ, seq.toString())
        saveClock(clock)
        pending.forEach { dao.removeFromOutbox(it.tableName, it.pk) }
        writeDeviceInfo(me)
        return changes.size
    }

    /** Все строки синхронизируемых таблиц — в outbox (базовый снимок устройства, с которого начинается синхронизация). */
    private fun enqueueAll() {
        SyncTriggers.TABLES.forEach { t ->
            sql.execSQL(
                "INSERT OR REPLACE INTO sync_outbox(table_name, pk) SELECT '${t.name}', ${t.pkExpr(t.name)} " +
                    "FROM `${t.name}` WHERE ${t.extraWhen(t.name)}",
            )
        }
    }

    // ---------------------------------------------------------------- применение

    private class Counters {
        var applied = 0
        var deferred = 0
        var refused = 0
    }

    private suspend fun import(): Result {
        val me = requireNotNull(deviceId())
        val incoming = mutableListOf<RowChange>()
        val newerSchema = mutableListOf<String>()
        val cursors = mutableMapOf<String, Long>()
        for (device in folder.list(SyncPaths.CHANGES).sorted()) {
            // Служебное чужих программ (`.stfolder` Syncthing, `.DS_Store` macOS) — не устройства.
            if (device == me || device.startsWith(".")) continue
            val cursor = settings.get(cursorKey(device))?.toLongOrNull() ?: 0L
            var last = cursor
            val files = folder.list(SyncPaths.changesDir(device))
                .mapNotNull { name -> SyncPaths.seqOf(name)?.let { it to name } }
                .filter { it.first > cursor }
                .sortedBy { it.first }
            for ((seq, name) in files) {
                // Битый/недописанный файл — дальше этого устройства пока не идём (порядок важен).
                val bytes = folder.read("${SyncPaths.changesDir(device)}/$name") ?: break
                val file = runCatching { syncJson.decodeFromString(ChangeFile.serializer(), bytes.decodeToString()) }.getOrNull() ?: break
                if (file.schemaVersion > AppDatabase.SCHEMA_VERSION) {
                    newerSchema += device
                    break
                }
                incoming += file.changes
                last = seq
            }
            if (last > cursor) cursors[device] = last
        }
        val deferredBefore = dao.deferred().mapNotNull {
            runCatching { syncJson.decodeFromString(RowChange.serializer(), it.payload) }.getOrNull()
        }
        if (incoming.isEmpty() && deferredBefore.isEmpty()) {
            return Result(newerSchemaDevices = newerSchema)
        }

        // Из нескольких версий одной строки (разные устройства/файлы/отложенные) — только самая новая.
        val latest = (deferredBefore + incoming)
            .groupBy { it.table to it.pk }
            .mapValues { (_, versions) -> versions.maxBy { Hlc.decode(it.hlc) } }
            .values
        var clock = loadClock(me)
        latest.forEach { change ->
            val remote = Hlc.decode(change.hlc)
            // Устройство с часами, ушедшими далеко вперёд, не должно утащить за собой часы всех остальных:
            // такая версия по-прежнему участвует в LWW, но свои часы по ней не двигаем.
            if (remote.wallMs <= now() + MAX_CLOCK_LEAD_MS) clock = clock.receive(remote, now())
        }
        saveClock(clock)

        val counters = Counters()
        dao.setState(SyncStateEntity(enabled = true, applying = true))
        sql.execSQL("PRAGMA defer_foreign_keys = ON")
        val redirects = dao.categoryRedirects().associate { it.fromId to it.intoId }.toMutableMap()

        latest.filter { !it.deleted }
            .sortedWith(compareBy({ TABLE_ORDER.indexOf(it.table) }, { if (it.table == "category" && it.row.str("parent_id") != null) 1 else 0 }))
            .forEach { applyUpsert(it, redirects, counters) }
        latest.filter { it.deleted }
            .sortedByDescending { TABLE_ORDER.indexOf(it.table) }
            .forEach { applyDelete(it, counters) }

        dao.setState(SyncStateEntity(enabled = true, applying = false))

        val problems = BackupRepository.validate(BackupRepository(db).buildPayload())
        if (problems.isNotEmpty()) throw SyncIntegrityException(problems)

        cursors.forEach { (device, seq) -> set(cursorKey(device), seq.toString()) }
        writeDeviceInfo(me)
        return Result(
            applied = counters.applied,
            deferred = counters.deferred,
            refused = counters.refused,
            newerSchemaDevices = newerSchema,
        )
    }

    private suspend fun isNewer(change: RowChange): Boolean {
        val local = dao.meta(change.table, change.pk) ?: return true
        return Hlc.decode(change.hlc) > Hlc.decode(local.hlc)
    }

    private suspend fun applyUpsert(change: RowChange, redirects: MutableMap<String, String>, counters: Counters) {
        if (!isNewer(change)) {
            dao.removeDeferred(change.table, change.pk)
            return
        }
        if (change.table == "app_setting" && !BackupRepository.isSyncedSetting(change.pk)) return
        if (change.table !in TABLE_ORDER) return // таблица из более новой версии — не знаем её
        val row = rewriteRedirects(change.table, requireNotNull(change.row), redirects)

        // Перенаправление — служебная запись: хранится всегда, как пришло (его сводит LWW), и никогда не откладывается.
        if (hasMissingRefs(change.table, row)) {
            dao.setDeferred(SyncDeferredEntity(change.table, change.pk, change.hlc, syncJson.encodeToString(RowChange.serializer(), change)))
            counters.deferred++
            return
        }
        if (change.table == "account" && changesCurrencyOfUsedAccount(change.pk, row)) {
            // I-15/I-23: валюта счёта с операциями неизменна — оставляем свою и переразсылаем её более новой версией.
            dao.setMeta(SyncRowMetaEntity(change.table, change.pk, change.hlc, deleted = false))
            rebroadcast(change.table, change.pk)
            dao.removeDeferred(change.table, change.pk)
            counters.refused++
            return
        }
        if (change.table == "category" && resolveCategoryNameClash(change, row, redirects)) {
            dao.removeDeferred(change.table, change.pk)
            counters.applied++
            return
        }

        writeRow(change.table, change.pk, row)
        dao.setMeta(SyncRowMetaEntity(change.table, change.pk, change.hlc, deleted = false))
        dao.removeDeferred(change.table, change.pk)
        // Строку пришлось перенаправить в другую категорию — это уже наша правка: рассылаем её более новой версией,
        // иначе у автора и у нас осталась бы одна и та же операция в разных категориях (I-26).
        if (row != change.row) rebroadcast(change.table, change.pk)
        if (change.table == "category_redirect") {
            val from = row.str("from_id")!!
            redirects[from] = row.str("into_id")!!
            // Переносим операции, только если конечная категория цепочки здесь есть; иначе они остаются где были
            // (их правки и так сводит LWW), а перенаправление пригодится опоздавшим.
            val target = resolve(from, redirects)
            if (target != from && readRow("category", target) != null) {
                asLocalChange {
                    repointCategory(from, target)
                    deleteCategoryIfUnused(from)
                }
            }
        }
        counters.applied++
    }

    private suspend fun applyDelete(change: RowChange, counters: Counters) {
        if (!isNewer(change)) {
            dao.removeDeferred(change.table, change.pk)
            return
        }
        dao.removeDeferred(change.table, change.pk)
        if (readRow(change.table, change.pk) != null && isReferenced(change.table, change.pk)) {
            // Ссылка сильнее удаления (§4): на строку уже ссылаются — оставляем и переразсылаем её.
            dao.setMeta(SyncRowMetaEntity(change.table, change.pk, change.hlc, deleted = false))
            rebroadcast(change.table, change.pk)
            counters.refused++
            return
        }
        sql.delete(change.table, "${pkWhere(change.table)} = ?", arrayOf(change.pk))
        dao.setMeta(SyncRowMetaEntity(change.table, change.pk, change.hlc, deleted = true))
        counters.applied++
    }

    /**
     * Две разные категории с одним именем и видом (`ux_category_name_kind`): считаются одной, побеждает меньший id —
     * правило одинаковое на всех устройствах, поэтому все придут к одной категории. Проигравшая — перенаправление.
     * @return true — изменение обработано здесь.
     */
    private suspend fun resolveCategoryNameClash(change: RowChange, row: JsonObject, redirects: MutableMap<String, String>): Boolean {
        val other = sql.query(
            "SELECT id FROM category WHERE name = ? AND kind = ? AND id <> ?",
            arrayOf(row.str("name"), row.str("kind"), change.pk),
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return false

        val winner = minOf(other, change.pk)
        val loser = maxOf(other, change.pk)
        if (winner == change.pk) {
            // Чужая категория побеждает: свою переводим на неё и удаляем — это рассылается как обычные правки.
            asLocalChange {
                repointCategory(other, change.pk)
                sql.delete("category", "id = ?", arrayOf(other))
            }
            writeRow("category", change.pk, row)
            dao.setMeta(SyncRowMetaEntity("category", change.pk, change.hlc, deleted = false))
        } else {
            // Своя побеждает: чужую не вставляем; её операции переедут к нам по перенаправлению.
            dao.setMeta(SyncRowMetaEntity("category", change.pk, change.hlc, deleted = true))
        }
        // OR IGNORE: перенаправление от слияния (сводится LWW) важнее выведенного из совпадения имён.
        asLocalChange {
            sql.execSQL("INSERT OR IGNORE INTO category_redirect(from_id, into_id) VALUES (?, ?)", arrayOf(loser, winner))
        }
        redirects.putIfAbsent(loser, winner)
        return true
    }

    private fun repointCategory(from: String, into: String) {
        val args = arrayOf(into, from)
        sql.execSQL("UPDATE txn SET category_id = ? WHERE category_id = ?", args)
        sql.execSQL("UPDATE budget SET category_id = ? WHERE category_id = ?", args)
        sql.execSQL("UPDATE recurrence SET category_id = ? WHERE category_id = ?", args)
        sql.execSQL("UPDATE category SET parent_id = ? WHERE parent_id = ?", args)
    }

    private fun deleteCategoryIfUnused(id: String) {
        if (readRow("category", id) != null && !isReferenced("category", id)) sql.delete("category", "id = ?", arrayOf(id))
    }

    private fun rewriteRedirects(table: String, row: JsonObject, redirects: Map<String, String>): JsonObject {
        val column = when (table) {
            "txn", "budget", "recurrence" -> "category_id"
            "category" -> "parent_id"
            else -> return row
        }
        val original = row.str(column) ?: return row
        val id = resolveExisting(original, redirects)
        return if (id == original) row else JsonObject(row + (column to JsonPrimitive(id)))
    }

    /**
     * Цепочка перенаправлений от [id]: X → Y → Z. Встречные слияния (X в Y на одном устройстве, Y в X на другом) дают
     * цикл — тогда его концом считается категория с меньшим id, одинаково на всех устройствах.
     * @return узлы цепочки по порядку и узлы, которые считаются её концом (один — либо весь цикл).
     */
    private fun chain(id: String, redirects: Map<String, String>): Pair<List<String>, List<String>> {
        val nodes = mutableListOf(id)
        while (nodes.size <= 32) {
            val next = redirects[nodes.last()] ?: return nodes to listOf(nodes.last())
            val seen = nodes.indexOf(next)
            if (seen >= 0) return nodes to nodes.subList(seen, nodes.size).sorted()
            nodes += next
        }
        return nodes to listOf(nodes.last())
    }

    /** Конечная категория цепочки перенаправлений. */
    private fun resolve(id: String, redirects: Map<String, String>): String = chain(id, redirects).second.first()

    /**
     * Самая дальняя по цепочке категория, которая здесь существует (в цикле — с меньшим id); ни одной — конец цепочки
     * (тогда строка отложится до прихода категории).
     */
    private fun resolveExisting(id: String, redirects: Map<String, String>): String {
        val (nodes, ends) = chain(id, redirects)
        ends.firstOrNull { readRow("category", it) != null }?.let { return it }
        return nodes.lastOrNull { readRow("category", it) != null } ?: ends.first()
    }

    private fun hasMissingRefs(table: String, row: JsonObject): Boolean =
        REFS[table].orEmpty().any { (column, target) ->
            val value = row.str(column) ?: return@any false
            val (targetTable, targetColumn) = target
            sql.query("SELECT 1 FROM `$targetTable` WHERE `$targetColumn` = ? LIMIT 1", arrayOf(value)).use { !it.moveToFirst() }
        }

    private fun isReferenced(table: String, pk: String): Boolean =
        REFERENCED_BY[table].orEmpty().any { (refTable, column) ->
            sql.query("SELECT 1 FROM `$refTable` WHERE `$column` = ? LIMIT 1", arrayOf(pk)).use { it.moveToFirst() }
        }

    private fun changesCurrencyOfUsedAccount(id: String, row: JsonObject): Boolean {
        val local = sql.query("SELECT currency_code FROM account WHERE id = ?", arrayOf(id))
            .use { if (it.moveToFirst()) it.getString(0) else null } ?: return false
        if (local == row.str("currency_code")) return false
        return sql.query("SELECT 1 FROM txn WHERE account_id = ? OR to_account_id = ? LIMIT 1", arrayOf(id, id)).use { it.moveToFirst() }
    }

    /**
     * Производные правки (перенос операций по перенаправлению, удаление опустевшей категории, разрешение одноимённых
     * категорий) — обычные локальные изменения: триггеры их захватывают, при выгрузке они получают версию новее всего
     * увиденного и расходятся по устройствам. Иначе итог зависел бы от порядка, в котором устройство получило файлы (I-26).
     */
    private suspend fun <T> asLocalChange(block: suspend () -> T): T {
        dao.setState(SyncStateEntity(enabled = true, applying = false))
        try {
            return block()
        } finally {
            dao.setState(SyncStateEntity(enabled = true, applying = true))
        }
    }

    private fun rebroadcast(table: String, pk: String) =
        sql.execSQL("INSERT OR REPLACE INTO sync_outbox(table_name, pk) VALUES (?, ?)", arrayOf(table, pk))

    // ---------------------------------------------------------------- строки целиком

    private fun pkWhere(table: String): String = SyncTriggers.TABLES.first { it.name == table }.pkExpr("`$table`")

    private fun readRow(table: String, pk: String): JsonObject? =
        sql.query("SELECT * FROM `$table` WHERE ${pkWhere(table)} = ?", arrayOf(pk)).use { c ->
            if (!c.moveToFirst()) null else JsonObject((0 until c.columnCount).associate { c.getColumnName(it) to c.value(it) })
        }

    private fun Cursor.value(i: Int) = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getString(i)) // в схеме REAL нет; на всякий случай — текстом, без потери
        else -> JsonPrimitive(getString(i))
    }

    private fun writeRow(table: String, pk: String, row: JsonObject) {
        val values = ContentValues()
        row.forEach { (column, value) ->
            when {
                value is JsonNull -> values.putNull(column)
                value is JsonPrimitive && value.isString -> values.put(column, value.content)
                value is JsonPrimitive -> value.longOrNull?.let { values.put(column, it) } ?: values.put(column, value.content)
            }
        }
        val updated = sql.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "${pkWhere(table)} = ?", arrayOf(pk))
        if (updated == 0) sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }

    // ---------------------------------------------------------------- служебное

    private suspend fun writeDeviceInfo(me: String) {
        val seen = folder.list(SyncPaths.CHANGES).filter { it != me && !it.startsWith(".") }
            .associateWith { settings.get(cursorKey(it))?.toLongOrNull() ?: 0L }
        val info = DeviceInfo(
            deviceId = me,
            name = settings.get(KEY_DEVICE_NAME).orEmpty(),
            schemaVersion = AppDatabase.SCHEMA_VERSION,
            lastSeq = settings.get(KEY_SEQ)?.toLongOrNull() ?: 0L,
            seen = seen,
            primary = isPrimary(),
        )
        folder.write(SyncPaths.device(me), syncJson.encodeToString(DeviceInfo.serializer(), info).toByteArray())
    }

    private suspend fun loadClock(me: String): Hlc =
        settings.get(KEY_HLC)?.let { runCatching { Hlc.decode(it) }.getOrNull() }?.copy(deviceId = me) ?: Hlc.zero(me)

    private suspend fun saveClock(clock: Hlc) = set(KEY_HLC, clock.encode())

    private suspend fun set(key: String, value: String) = settings.set(AppSettingEntity(key, value))

    companion object {
        // `sync.` — привязаны к устройству: триггеры их не захватывают, бэкап не переносит.
        const val KEY_DEVICE_ID = "sync.device_id"
        const val KEY_DEVICE_NAME = "sync.device_name"
        const val KEY_PRIMARY = "sync.primary"
        private const val KEY_HLC = "sync.hlc"
        private const val KEY_SEQ = "sync.seq"
        /** Насколько чужие часы могут опережать наши, чтобы мы под них подстраивались (сутки). */
        private const val MAX_CLOCK_LEAD_MS = 24L * 60 * 60 * 1000
        private fun cursorKey(device: String) = "sync.cursor.$device"

        /** Порядок применения вставок/правок (родители раньше детей); удаления — в обратном. */
        private val TABLE_ORDER = listOf(
            "currency", "account", "category", "category_redirect", "import_batch",
            "txn", "budget", "recurrence", "import_alias", "app_setting",
        )

        /** Ссылки строки: столбец → (таблица, столбец). Нет цели — изменение откладывается. */
        private val REFS: Map<String, List<Pair<String, Pair<String, String>>>> = mapOf(
            "account" to listOf("currency_code" to ("currency" to "code")),
            "category" to listOf("parent_id" to ("category" to "id")),
            "txn" to listOf(
                "account_id" to ("account" to "id"),
                "to_account_id" to ("account" to "id"),
                "currency_code" to ("currency" to "code"),
                "to_currency_code" to ("currency" to "code"),
                "category_id" to ("category" to "id"),
                "import_batch_id" to ("import_batch" to "id"),
            ),
            "budget" to listOf("category_id" to ("category" to "id"), "currency_code" to ("currency" to "code")),
            "recurrence" to listOf(
                "account_id" to ("account" to "id"),
                "category_id" to ("category" to "id"),
                "currency_code" to ("currency" to "code"),
            ),
        )

        /** Кто ссылается на строку таблицы: (таблица, столбец). Используемую строку удаление не трогает. */
        private val REFERENCED_BY: Map<String, List<Pair<String, String>>> = mapOf(
            "currency" to listOf(
                "account" to "currency_code", "txn" to "currency_code", "txn" to "to_currency_code",
                "budget" to "currency_code", "recurrence" to "currency_code",
            ),
            "account" to listOf("txn" to "account_id", "txn" to "to_account_id", "recurrence" to "account_id"),
            "category" to listOf(
                "txn" to "category_id", "budget" to "category_id", "recurrence" to "category_id", "category" to "parent_id",
            ),
            "import_batch" to listOf("txn" to "import_batch_id", "category" to "import_batch_id"),
        )
    }
}

private fun JsonObject?.str(column: String): String? =
    (this?.get(column) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
