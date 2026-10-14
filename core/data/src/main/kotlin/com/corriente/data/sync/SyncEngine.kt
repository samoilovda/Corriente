package com.corriente.data.sync

import com.corriente.data.db.writeTransaction
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
        /** Сколько своих пачек удалила автоматическая очистка папки (S5), 0 — не запускалась. */
        val cleanedFiles: Int = 0,
    )

    /** Итог очистки папки (S5, docs/SYNC_DESIGN.md §7). */
    data class CleanupResult(val deletedFiles: Int = 0, val removedTombstones: Int = 0)

    private val dao = db.syncDao()
    private val settings = db.appSettingDao()
    private val sql = SqlRunner(db)

    suspend fun isEnabled(): Boolean = dao.state()?.enabled == true

    suspend fun deviceId(): String? = settings.get(KEY_DEVICE_ID)

    suspend fun isPrimary(): Boolean = settings.get(KEY_PRIMARY) == "true"

    /**
     * Включает синхронизацию. [baseline] — выложить все текущие строки (устройство, с которого синхронизация начинается):
     * иначе данные, появившиеся до включения, никогда не попали бы в журнал (§6). [primary] — основное устройство (импорт
     * Monefy разрешён только на нём, §10).
     */
    suspend fun enable(name: String, primary: Boolean, baseline: Boolean) = db.writeTransaction {
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
    suspend fun wipeLocalData() = db.writeTransaction {
        check(!isEnabled()) { "disable sync before wiping local data" }
        listOf(
            "DELETE FROM txn", "DELETE FROM budget", "DELETE FROM recurrence", "DELETE FROM import_alias",
            "DELETE FROM category_redirect", "DELETE FROM category WHERE parent_id IS NOT NULL", "DELETE FROM category",
            "DELETE FROM account", "DELETE FROM import_batch", "DELETE FROM sync_row_meta", "DELETE FROM sync_deferred",
            "DELETE FROM sync_outbox",
        ).forEach { sql.exec(it) }
    }

    /** Есть ли на устройстве свои данные (счета/операции) — от этого зависит вопрос при подключении (§6). */
    suspend fun hasLocalData(): Boolean =
        sql.query("SELECT (SELECT COUNT(*) FROM account) + (SELECT COUNT(*) FROM txn)") { it.getLong(0) }.single() > 0

    /** Один обмен: выгрузить своё, применить чужое. */
    suspend fun sync(): Result {
        check(isEnabled()) { "sync is not enabled" }
        val exported = db.writeTransaction { export() }
        val result = db.writeTransaction {
            // Правка, сделанная между двумя транзакциями, должна получить версию до сравнения с чужими.
            val again = export()
            import().copy(exported = exported + again)
        }
        // S5: карточка устройства «живая», даже если ничего не менялось, — иначе простаивающее устройство через 90 дней
        // сочли бы пропавшим. Раз в сутки — автоматическая очистка, если своих пачек накопилось много.
        db.writeTransaction { heartbeat() }
        val cleaned = if (cleanupDue()) db.writeTransaction { cleanupNow() }.deletedFiles else 0
        return result.copy(cleanedFiles = cleaned)
    }

    /**
     * Очистка папки по кнопке (S5): сначала обычный обмен, затем — снимок этого устройства и удаление его пачек, которые
     * уже применили все остальные (кроме не появлявшихся больше 90 дней), и надгробий старше 90 дней.
     */
    suspend fun cleanup(): CleanupResult {
        sync()
        return db.writeTransaction { cleanupNow() }
    }

    private suspend fun cleanupDue(): Boolean {
        val me = deviceId() ?: return false
        val last = settings.get(KEY_CLEANUP_AT)?.toLongOrNull() ?: 0L
        if (now() - last < CLEANUP_INTERVAL_MS) return false
        return folder.list(SyncPaths.changesDir(me)).count { SyncPaths.seqOf(it) != null } > CLEANUP_FILE_THRESHOLD
    }

    private suspend fun heartbeat() {
        val me = deviceId() ?: return
        val last = settings.get(KEY_CARD_AT)?.toLongOrNull() ?: 0L
        if (now() - last >= HEARTBEAT_MS) writeDeviceInfo(me)
    }

    private suspend fun cleanupNow(): CleanupResult {
        val me = requireNotNull(deviceId())
        set(KEY_CLEANUP_AT, now().toString())
        val seq = settings.get(KEY_SEQ)?.toLongOrNull() ?: 0L

        var deleted = 0
        val mine = folder.list(SyncPaths.changesDir(me)).mapNotNull { name -> SyncPaths.seqOf(name)?.let { it to name } }
        val others = devices().filter { it.deviceId != me }
        if (others.isNotEmpty() && mine.isNotEmpty()) {
            // Пачку можно удалить, когда её применили все «живые» устройства; не появлявшиеся > 90 дней не блокируют.
            // Карточка без времени (старое приложение) считается живой. Нет ни одной живой — безопасно всё своё.
            val live = others.filter { it.updatedAt == 0L || now() - it.updatedAt <= STALE_MS }
            val safe = live.minOfOrNull { it.seen[me] ?: 0L } ?: seq
            val deletable = mine.filter { it.first <= safe }
            if (deletable.isNotEmpty()) {
                // Снимок — до удаления: кому пачек не хватит, тот подключится через него.
                val snapshot = SnapshotFile(schemaVersion = AppDatabase.SCHEMA_VERSION, deviceId = me, seq = seq, changes = snapshotChanges())
                folder.write(SyncPaths.snapshot(me), syncJson.encodeToString(SnapshotFile.serializer(), snapshot).toByteArray())
                deletable.forEach { (_, name) -> folder.delete("${SyncPaths.changesDir(me)}/$name") }
                deleted = deletable.size
            }
        }

        // Надгробия старше 90 дней (по времени HLC) больше не нужны.
        val cutoff = String.format(java.util.Locale.ROOT, "%013d", maxOf(0L, now() - STALE_MS))
        val oldTombstones = sql.query("SELECT COUNT(*) FROM sync_row_meta WHERE deleted = 1 AND hlc < ?", cutoff) { it.getLong(0) }.single()
        if (oldTombstones > 0) sql.exec("DELETE FROM sync_row_meta WHERE deleted = 1 AND hlc < ?", cutoff)
        writeDeviceInfo(me)
        return CleanupResult(deleted, oldTombstones.toInt())
    }

    /** Всё состояние с версиями. Строки, ждущие выгрузки (`sync_outbox`), не входят — они уйдут обычной пачкой после [seq]. */
    private suspend fun snapshotChanges(): List<RowChange> {
        val pending = dao.outbox().map { it.tableName to it.pk }.toSet()
        val metas = sql.query("SELECT table_name, pk, hlc, deleted FROM sync_row_meta") {
            RowChange(it.getText(0), it.getText(1), it.getText(2), deleted = it.getLong(3) != 0L)
        }
        return metas.mapNotNull { m ->
            when {
                (m.table to m.pk) in pending -> null
                m.deleted -> m
                else -> readRow(m.table, m.pk)?.let { m.copy(row = it) }
            }
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
    private suspend fun enqueueAll() {
        SyncTriggers.TABLES.forEach { t ->
            sql.exec(
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
            val all = folder.list(SyncPaths.changesDir(device))
                .mapNotNull { name -> SyncPaths.seqOf(name)?.let { it to name } }
                .sortedBy { it.first }
            // S5: первых нужных пачек уже нет (устройство их убрало при очистке) — сначала снимок этого устройства.
            if (all.firstOrNull()?.first?.let { it > cursor + 1 } != false) {
                val snapshot = readSnapshot(device)
                if (snapshot != null && snapshot.seq > cursor) {
                    if (snapshot.schemaVersion > AppDatabase.SCHEMA_VERSION || snapshot.formatVersion > ChangeFile.FORMAT_VERSION) {
                        newerSchema += device
                        continue
                    }
                    incoming += snapshot.changes
                    last = snapshot.seq
                }
            }
            val files = all.filter { it.first > last }
            for ((seq, name) in files) {
                // Битый/недописанный файл — дальше этого устройства пока не идём (порядок важен).
                val bytes = folder.read("${SyncPaths.changesDir(device)}/$name") ?: break
                val file = runCatching { syncJson.decodeFromString(ChangeFile.serializer(), bytes.decodeToString()) }.getOrNull() ?: break
                if (file.schemaVersion > AppDatabase.SCHEMA_VERSION || file.formatVersion > ChangeFile.FORMAT_VERSION) {
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
            cursors.forEach { (device, seq) -> set(cursorKey(device), seq.toString()) }
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
        sql.exec("PRAGMA defer_foreign_keys = ON")
        val redirects = dao.categoryRedirects().associate { it.fromId to it.intoId }.toMutableMap()

        latest.filter { !it.deleted }
            .sortedWith(compareBy({ TABLE_ORDER.indexOf(it.table) }, { if (it.table == "category" && it.row.str("parent_id") != null) 1 else 0 }))
            .forEach { applyUpsert(it, redirects, counters) }
        latest.filter { it.deleted }
            .sortedByDescending { TABLE_ORDER.indexOf(it.table) }
            .forEach { applyDelete(it, counters) }

        // Перенаправление могло прийти раньше, чем конечная категория, — доделываем слияние, когда цепочка уже целиком здесь.
        sweepRedirectedCategories(redirects)
        // T3: перенаправление категории могло свести два уже существующих бюджета в один ключ — доводим до одного.
        dedupeBudgets()

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

    private fun readSnapshot(device: String): SnapshotFile? =
        folder.read(SyncPaths.snapshot(device))
            ?.let { runCatching { syncJson.decodeFromString(SnapshotFile.serializer(), it.decodeToString()) }.getOrNull() }

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
        if (change.table == "budget" && resolveBudgetClash(change, row)) {
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
        sql.exec("DELETE FROM `${change.table}` WHERE ${pkWhere(change.table)} = ?", change.pk)
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
            row.str("name"), row.str("kind"), change.pk,
        ) { it.getText(0) }.firstOrNull() ?: return false

        val winner = minOf(other, change.pk)
        val loser = maxOf(other, change.pk)
        if (winner == change.pk) {
            // Чужая категория побеждает: свою переводим на неё и удаляем — это рассылается как обычные правки.
            asLocalChange {
                repointCategory(other, change.pk)
                sql.exec("DELETE FROM category WHERE id = ?", other)
            }
            writeRow("category", change.pk, row)
            dao.setMeta(SyncRowMetaEntity("category", change.pk, change.hlc, deleted = false))
        } else {
            // Своя побеждает: чужую не вставляем; её операции переедут к нам по перенаправлению.
            dao.setMeta(SyncRowMetaEntity("category", change.pk, change.hlc, deleted = true))
        }
        // OR IGNORE: перенаправление от слияния (сводится LWW) важнее выведенного из совпадения имён.
        asLocalChange {
            sql.exec("INSERT OR IGNORE INTO category_redirect(from_id, into_id) VALUES (?, ?)", loser, winner)
        }
        redirects.putIfAbsent(loser, winner)
        return true
    }

    /**
     * Два разных бюджета с одним ключом `(категория, валюта, период)` (категория может быть `NULL` — «на всё»)
     * считаются одним: побеждает меньший id, как с категориями. На бюджеты никто не ссылается — перенаправления не нужны.
     * Ключ сравнивается по уже перенаправленной категории [row] (бюджет мог прийти на слитую категорию).
     * @return true — изменение обработано здесь.
     */
    private suspend fun resolveBudgetClash(change: RowChange, row: JsonObject): Boolean {
        val other = sql.query(
            "SELECT id FROM budget WHERE category_id IS ? AND currency_code = ? AND period = ? AND id <> ? ORDER BY id",
            row.str("category_id"), row.str("currency_code"), row.str("period"), change.pk,
        ) { it.getText(0) }.firstOrNull() ?: return false

        if (change.pk < other) {
            // Чужой бюджет побеждает: свой удаляем как обычную локальную правку (расходится по устройствам).
            asLocalChange { sql.exec("DELETE FROM budget WHERE id = ?", other) }
            writeRow("budget", change.pk, row)
            dao.setMeta(SyncRowMetaEntity("budget", change.pk, change.hlc, deleted = false))
        } else {
            // Свой побеждает: чужой не вставляем, ставим надгробие — его удаление у автора придёт следующим обменом.
            // Если это правка уже существующей у нас строки, ключ которой стал занят, — её тоже убираем (как локальную
            // правку: удаление расходится), иначе строка осталась бы здесь в старом виде, а у других — исчезла.
            if (readRow("budget", change.pk) != null) asLocalChange { sql.exec("DELETE FROM budget WHERE id = ?", change.pk) }
            dao.setMeta(SyncRowMetaEntity("budget", change.pk, change.hlc, deleted = true))
            // Надгробие рассылаем сами (новой версией): иначе, если победитель потом исчезнет раньше, чем дойдёт до
            // автора проигравшего, у автора она осталась бы жить, а у остальных — нет.
            rebroadcast("budget", change.pk)
        }
        return true
    }

    /**
     * Страховка к [resolveBudgetClash]: дубликаты ключа бюджета, возникшие не от прихода строки (например, перенос
     * бюджетов при слиянии категорий), — остаётся меньший id. Правило детерминировано, поэтому устройства сходятся.
     */
    private suspend fun dedupeBudgets() {
        val hasDuplicates = sql.exists(
            "SELECT 1 FROM budget b WHERE EXISTS (SELECT 1 FROM budget o WHERE o.id < b.id AND o.category_id IS b.category_id " +
                "AND o.currency_code = b.currency_code AND o.period = b.period) LIMIT 1",
        )
        if (!hasDuplicates) return
        asLocalChange {
            sql.exec(
                "DELETE FROM budget WHERE id IN (SELECT b.id FROM budget b WHERE EXISTS (SELECT 1 FROM budget o WHERE o.id < b.id " +
                    "AND o.category_id IS b.category_id AND o.currency_code = b.currency_code AND o.period = b.period))",
            )
        }
    }

    /**
     * Категории, у которых есть перенаправление и конечная категория цепочки уже здесь: переносим их операции/бюджеты и
     * удаляем, если опустели — то же, что делается в момент прихода перенаправления ([applyUpsert]), но и для случая,
     * когда конечная категория пришла позже. Правило детерминировано.
     */
    private suspend fun sweepRedirectedCategories(redirects: Map<String, String>) {
        for (from in redirects.keys.toList()) {
            if (readRow("category", from) == null) continue
            val target = resolve(from, redirects)
            if (target == from || readRow("category", target) == null) continue
            asLocalChange {
                repointCategory(from, target)
                deleteCategoryIfUnused(from)
            }
        }
    }

    private suspend fun repointCategory(from: String, into: String) {
        sql.exec("UPDATE txn SET category_id = ? WHERE category_id = ?", into, from)
        sql.exec("UPDATE budget SET category_id = ? WHERE category_id = ?", into, from)
        sql.exec("UPDATE recurrence SET category_id = ? WHERE category_id = ?", into, from)
        sql.exec("UPDATE category SET parent_id = ? WHERE parent_id = ?", into, from)
    }

    private suspend fun deleteCategoryIfUnused(id: String) {
        if (readRow("category", id) != null && !isReferenced("category", id)) sql.exec("DELETE FROM category WHERE id = ?", id)
    }

    private suspend fun rewriteRedirects(table: String, row: JsonObject, redirects: Map<String, String>): JsonObject {
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
    private suspend fun resolveExisting(id: String, redirects: Map<String, String>): String {
        val (nodes, ends) = chain(id, redirects)
        ends.firstOrNull { readRow("category", it) != null }?.let { return it }
        return nodes.lastOrNull { readRow("category", it) != null } ?: ends.first()
    }

    private suspend fun hasMissingRefs(table: String, row: JsonObject): Boolean =
        REFS[table].orEmpty().any { (column, target) ->
            val value = row.str(column) ?: return@any false
            val (targetTable, targetColumn) = target
            !sql.exists("SELECT 1 FROM `$targetTable` WHERE `$targetColumn` = ? LIMIT 1", value)
        }

    private suspend fun isReferenced(table: String, pk: String): Boolean =
        REFERENCED_BY[table].orEmpty().any { (refTable, column) ->
            sql.exists("SELECT 1 FROM `$refTable` WHERE `$column` = ? LIMIT 1", pk)
        }

    private suspend fun changesCurrencyOfUsedAccount(id: String, row: JsonObject): Boolean {
        val local = sql.query("SELECT currency_code FROM account WHERE id = ?", id) { it.getText(0) }.firstOrNull() ?: return false
        if (local == row.str("currency_code")) return false
        return sql.exists("SELECT 1 FROM txn WHERE account_id = ? OR to_account_id = ? LIMIT 1", id, id)
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

    private suspend fun rebroadcast(table: String, pk: String) =
        sql.exec("INSERT OR REPLACE INTO sync_outbox(table_name, pk) VALUES (?, ?)", table, pk)

    // ---------------------------------------------------------------- строки целиком

    private fun pkWhere(table: String): String = SyncTriggers.TABLES.first { it.name == table }.pkExpr("`$table`")

    private suspend fun readRow(table: String, pk: String): JsonObject? =
        sql.query("SELECT * FROM `$table` WHERE ${pkWhere(table)} = ?", pk) { st ->
            JsonObject((0 until st.getColumnCount()).associate { i ->
                st.getColumnName(i) to when (st.getColumnType(i)) {
                    SqlRunner.TYPE_NULL -> JsonNull
                    SqlRunner.TYPE_INTEGER -> JsonPrimitive(st.getLong(i))
                    // в схеме REAL нет; на всякий случай — текстом, без потери точности
                    else -> JsonPrimitive(st.getText(i))
                }
            })
        }.firstOrNull()

    /** Строка целиком: UPDATE по ключу, а если такой строки нет — INSERT (UPSERT в SQLite Android 8 нет). */
    private suspend fun writeRow(table: String, pk: String, row: JsonObject) {
        val columns = row.keys.toList()
        val values: Array<Any?> = columns.map { column ->
            when (val value = row.getValue(column)) {
                is JsonNull -> null
                is JsonPrimitive -> if (value.isString) value.content else value.longOrNull ?: value.content
                else -> value.toString()
            }
        }.toTypedArray()
        sql.exec(
            "UPDATE `$table` SET ${columns.joinToString { "`$it` = ?" }} WHERE ${pkWhere(table)} = ?",
            *values, pk,
        )
        if (sql.changes() == 0L) {
            sql.exec(
                "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})",
                *values,
            )
        }
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
            updatedAt = now(),
        )
        folder.write(SyncPaths.device(me), syncJson.encodeToString(DeviceInfo.serializer(), info).toByteArray())
        set(KEY_CARD_AT, now().toString())
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
        private const val KEY_CARD_AT = "sync.card_at"
        private const val KEY_CLEANUP_AT = "sync.cleanup_at"
        /** S5: не появлявшееся дольше устройство не блокирует очистку; надгробия старше удаляются (90 суток). */
        private const val STALE_MS = 90L * 24 * 60 * 60 * 1000
        /** Автоочистка — не чаще раза в сутки и только когда своих пачек больше порога. */
        private const val CLEANUP_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val CLEANUP_FILE_THRESHOLD = 200
        /** Карточку без изменений перезаписываем раз в 12 часов — чтобы устройство не казалось пропавшим. */
        private const val HEARTBEAT_MS = 12L * 60 * 60 * 1000
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
