package com.corriente.data.backup

import androidx.room.withTransaction
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.db.entity.BudgetEntity
import com.corriente.data.db.entity.BudgetPeriod
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.ImportAliasEntity
import com.corriente.data.db.entity.ImportAliasKind
import com.corriente.data.db.entity.ImportBatchEntity
import com.corriente.data.db.entity.RecurrenceEntity
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.toDomain
import com.corriente.data.repository.toDomain
import com.corriente.money.CurrencyCode
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/**
 * Экспорт/восстановление бэкапа для ViewModel (F2.7) — интерфейс, чтобы состояние занятости
 * можно было проверить юнит-тестом без Room.
 */
interface BackupIo {
    suspend fun export(output: OutputStream)
    suspend fun restore(input: InputStream, beforeReplace: suspend () -> Unit = {})
}

/**
 * Полный экспорт/восстановление БД в файл (T1.9, I-21). Пишется и читается через SAF —
 * вызывающий код (:app) сам открывает поток на выбранный пользователем файл, репозиторий
 * знает только про [InputStream]/[OutputStream], а не про `Uri`/`ContentResolver` (I-24 заодно:
 * это гарантирует, что бэкап не превратится незаметно в "заодно и в облако").
 *
 * Восстановление **полностью замещает** текущие данные — экран T1.9 обязан спросить
 * подтверждение до вызова [restore].
 */
class BackupRepository(private val db: AppDatabase) : BackupIo {

    /** Полезная нагрузка бэкапа, собранная из текущего состояния [db] (T1.9). */
    suspend fun buildPayload(): BackupPayload = BackupPayload(
        schemaVersion = SCHEMA_VERSION,
        exportedAt = System.currentTimeMillis(),
        currencies = db.currencyDao().observeAll().first().map { it.toBackup() },
        // observeAll, не observeActive: бэкап обязан сохранять состояние ПОЛНОСТЬЮ (I-21),
        // включая архивные счета и категории.
        accounts = db.accountDao().observeAll().first().map { it.toBackup() },
        categories = db.categoryDao().observeAll().first().map { it.toBackup() },
        transactions = db.txnDao().observeAll().first().map { it.toBackup() },
        importBatches = db.importBatchDao().getAll().map { it.toBackup() },
        importAliases = db.importAliasDao().getAll().map { it.toBackup() },
        appSettings = db.appSettingDao().getAll().map { it.toBackup() },
        budgets = db.budgetDao().getAll().map { it.toBackup() },
        recurrences = db.recurrenceDao().getAll().map { it.toBackup() },
    )

    override suspend fun export(output: OutputStream) {
        val payload = buildPayload()
        output.writer(Charsets.UTF_8).use { it.write(json.encodeToString(BackupPayload.serializer(), payload)) }
    }

    /**
     * @throws BackupVersionException если версия схемы файла новее той, что умеет читать
     * текущая версия приложения (I-21: старые версии обязаны читаться через миграции, а не
     * этот путь; более новые читать безопасно нельзя в принципе).
     */
    override suspend fun restore(input: InputStream, beforeReplace: suspend () -> Unit) {
        restorePayload(parsePayload(input), beforeReplace)
    }

    /**
     * Замещает данные в [db] содержимым [payload] (F1.4/R1.4). Вынесено из [restore] отдельно,
     * чтобы «Проверить файл» (R1.4) могло прогнать тот же путь на временной in-memory БД без
     * лишнего сериализованного круга через JSON.
     *
     * @throws BackupVersionException / [BackupInvalidException] — как у [restore].
     */
    suspend fun restorePayload(payload: BackupPayload, beforeReplace: suspend () -> Unit = {}) {
        if (payload.schemaVersion > SCHEMA_VERSION) {
            throw BackupVersionException(payload.schemaVersion, SCHEMA_VERSION)
        }
        // F1.4: целостность проверяется ДО удаления текущих данных, а не полагается на внешние
        // ключи SQLite уже внутри транзакции.
        val problems = validate(payload)
        if (problems.isNotEmpty()) throw BackupInvalidException(problems)
        // Копия текущей БД на случай, если восстановление всё же не задастся (F1.4).
        beforeReplace()
        db.withTransaction {
            // Порядок удаления - по внешним ключам, от зависимых к независимым.
            db.recurrenceDao().deleteAll()
            db.budgetDao().deleteAll()
            db.txnDao().deleteAll()
            db.importBatchDao().deleteAll()
            db.importAliasDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.appSettingDao().deleteAll()
            db.currencyDao().deleteAll()

            // И обратно - от независимых к зависимым. import_batch обязан лечь ДО txn:
            // txn.import_batch_id — настоящий Room @ForeignKey на import_batch(id) (TxnEntity.kt),
            // и SQLite проверяет его сразу при INSERT (см. регрессию: восстановление любого
            // бэкапа с хотя бы одной импортированной операцией падало SQLITE_CONSTRAINT_FOREIGNKEY).
            db.currencyDao().insertAll(payload.currencies.map { it.toEntity() })
            payload.accounts.forEach { db.accountDao().insert(it.toEntity()) }
            payload.categories.forEach { db.categoryDao().insert(it.toEntity()) }
            payload.importBatches.forEach { db.importBatchDao().insert(it.toEntity()) }
            db.txnDao().insertAll(payload.transactions.map { it.toEntity() })
            payload.importAliases.forEach { db.importAliasDao().upsert(it.toEntity()) }
            payload.appSettings.forEach { db.appSettingDao().set(it.toEntity()) }
            payload.budgets.forEach { db.budgetDao().insert(it.toEntity()) }
            payload.recurrences.forEach { db.recurrenceDao().insert(it.toEntity()) }
        }
    }

    /** Контрольные суммы текущего состояния [db] (R1.4) — тот же счёт, что и по файлу бэкапа. */
    suspend fun currentSummary(): BackupSummary = summarize(buildPayload())

    companion object {
        // v2: category.import_batch_id (F1.5). v3: txn_fts (R2.1, не входит в бэкап — производный
        // кэш, I-9). v4: budget (R2.3). v5: recurrence (R2.4). v6: только данные — типовые
        // категории «из коробки», структура бэкапа не меняется. v7: только токенайзер txn_fts (A1.10).
        // Версия формата бэкапа = версия схемы БД (A3.2) — один источник.
        const val SCHEMA_VERSION = com.corriente.data.db.AppDatabase.SCHEMA_VERSION

        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        /** Разбор файла бэкапа в [BackupPayload] без записи куда-либо — используется restore и R1.4. */
        fun parsePayload(input: InputStream): BackupPayload = input.reader(Charsets.UTF_8).use {
            json.decodeFromString(BackupPayload.serializer(), it.readText())
        }

        /**
         * Контрольные суммы бэкапа (R1.4): число счетов/категорий/операций и сумма движений по
         * каждой валюте. Переводы исключены из сумм — как и в отчётах (I-11), это не доход и не
         * расход. Сложение — `Math.addExact` (I-3): переполнение обязано падать, а не тихо
         * переворачиваться. Чистая функция над [BackupPayload] — тестируется без БД.
         */
        fun summarize(payload: BackupPayload): BackupSummary {
            val sums = mutableMapOf<String, Long>()
            payload.transactions.forEach { t ->
                if (t.kind == "TRANSFER") return@forEach
                val delta = if (t.kind == "INCOME") t.amountMinor else -t.amountMinor
                sums[t.currencyCode] = Math.addExact(sums[t.currencyCode] ?: 0L, delta)
            }
            return BackupSummary(
                accounts = payload.accounts.size,
                categories = payload.categories.size,
                transactions = payload.transactions.size,
                sumsByCurrency = sums,
            )
        }

        /**
         * Проверка полезной нагрузки бэкапа до записи (F1.4, A1.12). Чистая функция — тестируется без БД.
         * @return структурированные проблемы ([BackupProblem]); пустой список — файл можно восстанавливать:
         * `toEntity()`, вставка и `toDomain()` для каждой записи гарантированно проходят.
         */
        fun validate(payload: BackupPayload): List<BackupProblem> {
            val problems = mutableListOf<BackupProblem>()
            fun invalid(e: BackupEntity, ref: String, f: BackupField, v: Any?) {
                problems += BackupProblem.InvalidValue(e, ref, f, v.toString())
            }
            fun dangling(e: BackupEntity, ref: String, t: BackupEntity, id: String) {
                problems += BackupProblem.DanglingReference(e, ref, t, id)
            }
            fun <T> dupes(entity: BackupEntity, items: List<T>, key: (T) -> String?) {
                val seen = HashSet<String>()
                items.forEach { item ->
                    val k = key(item) ?: return@forEach
                    if (!seen.add(k)) problems += BackupProblem.DuplicateKey(entity, k)
                }
            }
            fun codeFormatOk(code: String) = runCatching { CurrencyCode(code) }.isSuccess
            fun <E : Enum<E>> enumOk(values: Array<E>, name: String) = values.any { it.name == name }
            fun dateOk(text: String) = runCatching { LocalDate.parse(text) }.isSuccess

            // --- уникальность (иначе вставка падает на PRIMARY KEY / уникальном индексе) ---
            dupes(BackupEntity.CURRENCY, payload.currencies) { it.code }
            dupes(BackupEntity.ACCOUNT, payload.accounts) { it.id }
            dupes(BackupEntity.CATEGORY, payload.categories) { it.id }
            dupes(BackupEntity.CATEGORY, payload.categories) { "${it.name}|${it.kind}" }
            dupes(BackupEntity.TRANSACTION, payload.transactions) { it.id }
            dupes(BackupEntity.TRANSACTION, payload.transactions) { it.importHash }
            dupes(BackupEntity.IMPORT_BATCH, payload.importBatches) { it.id }
            dupes(BackupEntity.BUDGET, payload.budgets) { it.id }
            dupes(BackupEntity.RECURRENCE, payload.recurrences) { it.id }

            // --- справочник валют ---
            payload.currencies.forEach { c ->
                if (!codeFormatOk(c.code)) invalid(BackupEntity.CURRENCY, c.code, BackupField.CURRENCY_CODE, c.code)
                if (c.minorUnits !in 0..4) invalid(BackupEntity.CURRENCY, c.code, BackupField.MINOR_UNITS, c.minorUnits)
                else if (c.displayScale !in 0..c.minorUnits) {
                    invalid(BackupEntity.CURRENCY, c.code, BackupField.DISPLAY_SCALE, c.displayScale)
                }
            }
            val currencyCodes = payload.currencies.mapTo(HashSet()) { it.code }
            val accountCurrency = payload.accounts.associate { it.id to it.currencyCode }
            val accountIds = accountCurrency.keys
            val categoryById = payload.categories.associateBy { it.id }
            val batchIds = payload.importBatches.mapTo(HashSet()) { it.id }

            payload.accounts.forEach { a ->
                if (!codeFormatOk(a.currencyCode)) invalid(BackupEntity.ACCOUNT, a.id, BackupField.CURRENCY_CODE, a.currencyCode)
                else if (a.currencyCode !in currencyCodes) {
                    dangling(BackupEntity.ACCOUNT, a.id, BackupEntity.CURRENCY, a.currencyCode)
                }
                if (!enumOk(AccountKind.values(), a.kind)) invalid(BackupEntity.ACCOUNT, a.id, BackupField.ACCOUNT_KIND, a.kind)
            }
            payload.categories.forEach { c ->
                if (!enumOk(CategoryKind.values(), c.kind)) invalid(BackupEntity.CATEGORY, c.id, BackupField.KIND, c.kind)
                if (!enumOk(CategoryOrigin.values(), c.origin)) invalid(BackupEntity.CATEGORY, c.id, BackupField.ORIGIN, c.origin)
                if (c.parentId != null) {
                    val parent = categoryById[c.parentId]
                    when {
                        parent == null -> dangling(BackupEntity.CATEGORY, c.id, BackupEntity.CATEGORY, c.parentId)
                        c.parentId == c.id || parent.parentId != null -> problems += BackupProblem.NestedCategory(c.id)
                    }
                }
            }
            payload.importAliases.forEach { al ->
                val ref = "${al.sourceApp}/${al.sourceValue}"
                if (!enumOk(ImportAliasKind.values(), al.kind)) invalid(BackupEntity.IMPORT_ALIAS, ref, BackupField.ALIAS_KIND, al.kind)
            }

            payload.transactions.forEach { t ->
                val ref = t.id
                if (t.accountId !in accountIds) dangling(BackupEntity.TRANSACTION, ref, BackupEntity.ACCOUNT, t.accountId)
                if (!codeFormatOk(t.currencyCode)) invalid(BackupEntity.TRANSACTION, ref, BackupField.CURRENCY_CODE, t.currencyCode)
                else if (t.currencyCode !in currencyCodes) {
                    dangling(BackupEntity.TRANSACTION, ref, BackupEntity.CURRENCY, t.currencyCode)
                }
                accountCurrency[t.accountId]?.let { expected ->
                    if (t.currencyCode != expected) {
                        problems += BackupProblem.CurrencyMismatch(BackupEntity.TRANSACTION, ref, t.currencyCode, expected)
                    }
                }
                if (t.categoryId != null && t.categoryId !in categoryById) {
                    dangling(BackupEntity.TRANSACTION, ref, BackupEntity.CATEGORY, t.categoryId)
                }
                if (t.importBatchId != null && t.importBatchId !in batchIds) {
                    dangling(BackupEntity.TRANSACTION, ref, BackupEntity.IMPORT_BATCH, t.importBatchId)
                }
                if (t.amountMinor <= 0) problems += BackupProblem.NonPositiveAmount(BackupEntity.TRANSACTION, ref, t.amountMinor)
                if (!dateOk(t.date)) invalid(BackupEntity.TRANSACTION, ref, BackupField.DATE, t.date)
                when (t.kind) {
                    "TRANSFER" -> {
                        if (t.toAccountId == null || t.toAmountMinor == null || t.toCurrencyCode == null) {
                            problems += BackupProblem.BadTransfer(ref, TransferIssue.MISSING_SECOND_SIDE)
                        } else {
                            if (t.toAccountId !in accountIds) dangling(BackupEntity.TRANSACTION, ref, BackupEntity.ACCOUNT, t.toAccountId)
                            if (t.toAccountId == t.accountId) problems += BackupProblem.BadTransfer(ref, TransferIssue.SAME_ACCOUNT)
                            if (!codeFormatOk(t.toCurrencyCode)) invalid(BackupEntity.TRANSACTION, ref, BackupField.TO_CURRENCY_CODE, t.toCurrencyCode)
                            else if (t.toCurrencyCode !in currencyCodes) {
                                dangling(BackupEntity.TRANSACTION, ref, BackupEntity.CURRENCY, t.toCurrencyCode)
                            }
                            accountCurrency[t.toAccountId]?.let { expected ->
                                if (t.toCurrencyCode != expected) {
                                    problems += BackupProblem.CurrencyMismatch(BackupEntity.TRANSACTION, ref, t.toCurrencyCode, expected)
                                }
                            }
                            if (t.toAmountMinor <= 0) {
                                problems += BackupProblem.NonPositiveAmount(BackupEntity.TRANSACTION, ref, t.toAmountMinor)
                            }
                        }
                        if (t.categoryId != null) problems += BackupProblem.BadTransfer(ref, TransferIssue.HAS_CATEGORY)
                    }

                    "EXPENSE", "INCOME" -> {
                        if (t.toAccountId != null || t.toAmountMinor != null || t.toCurrencyCode != null) {
                            problems += BackupProblem.UnexpectedSecondSide(ref)
                        }
                    }

                    else -> invalid(BackupEntity.TRANSACTION, ref, BackupField.KIND, t.kind)
                }
            }
            payload.budgets.forEach { b ->
                val ref = b.id
                if (!codeFormatOk(b.currencyCode)) invalid(BackupEntity.BUDGET, ref, BackupField.CURRENCY_CODE, b.currencyCode)
                else if (b.currencyCode !in currencyCodes) dangling(BackupEntity.BUDGET, ref, BackupEntity.CURRENCY, b.currencyCode)
                if (b.categoryId != null && b.categoryId !in categoryById) {
                    dangling(BackupEntity.BUDGET, ref, BackupEntity.CATEGORY, b.categoryId)
                }
                if (b.amountMinor < 0) problems += BackupProblem.NonPositiveAmount(BackupEntity.BUDGET, ref, b.amountMinor)
                if (!dateOk(b.startsOn)) invalid(BackupEntity.BUDGET, ref, BackupField.DATE, b.startsOn)
                if (b.period != "MONTH") invalid(BackupEntity.BUDGET, ref, BackupField.PERIOD, b.period)
            }
            payload.recurrences.forEach { r ->
                val ref = r.id
                if (r.accountId !in accountIds) dangling(BackupEntity.RECURRENCE, ref, BackupEntity.ACCOUNT, r.accountId)
                if (!codeFormatOk(r.currencyCode)) invalid(BackupEntity.RECURRENCE, ref, BackupField.CURRENCY_CODE, r.currencyCode)
                else if (r.currencyCode !in currencyCodes) {
                    dangling(BackupEntity.RECURRENCE, ref, BackupEntity.CURRENCY, r.currencyCode)
                }
                accountCurrency[r.accountId]?.let { expected ->
                    if (r.currencyCode != expected) {
                        problems += BackupProblem.CurrencyMismatch(BackupEntity.RECURRENCE, ref, r.currencyCode, expected)
                    }
                }
                if (r.categoryId != null && r.categoryId !in categoryById) {
                    dangling(BackupEntity.RECURRENCE, ref, BackupEntity.CATEGORY, r.categoryId)
                }
                if (r.amountMinor <= 0) problems += BackupProblem.NonPositiveAmount(BackupEntity.RECURRENCE, ref, r.amountMinor)
                if (r.kind !in setOf("EXPENSE", "INCOME")) invalid(BackupEntity.RECURRENCE, ref, BackupField.KIND, r.kind)
                if (!dateOk(r.nextRunOn)) invalid(BackupEntity.RECURRENCE, ref, BackupField.DATE, r.nextRunOn)
                when (r.ruleType) {
                    "DAY_OF_MONTH" -> if (r.dayOfMonth == null || r.dayOfMonth !in 1..31) {
                        invalid(BackupEntity.RECURRENCE, ref, BackupField.DAY_OF_MONTH, r.dayOfMonth)
                    }
                    "EVERY_N_DAYS" -> if (r.intervalDays == null || r.intervalDays < 1) {
                        invalid(BackupEntity.RECURRENCE, ref, BackupField.INTERVAL, r.intervalDays)
                    }
                    else -> invalid(BackupEntity.RECURRENCE, ref, BackupField.RULE_TYPE, r.ruleType)
                }
            }

            // Страховка: если явные проверки что-то упустили — не даём файлу дойти до БД, где `toDomain()`
            // уронит каждый экран. Только для записей без найденных проблем (иначе дубль сообщений).
            if (problems.isEmpty()) {
                fun <T> readable(entity: BackupEntity, items: List<T>, ref: (T) -> String, read: (T) -> Unit) {
                    items.forEach { runCatching { read(it) }.onFailure { e ->
                        problems += BackupProblem.Unreadable(entity, ref(it), e.message ?: e.javaClass.simpleName)
                    } }
                }
                readable(BackupEntity.CURRENCY, payload.currencies, { it.code }) { it.toEntity().toDomain() }
                readable(BackupEntity.ACCOUNT, payload.accounts, { it.id }) { it.toEntity().toDomain() }
                readable(BackupEntity.CATEGORY, payload.categories, { it.id }) { it.toEntity().toDomain() }
                readable(BackupEntity.TRANSACTION, payload.transactions, { it.id }) { it.toEntity().toDomain() }
                readable(BackupEntity.BUDGET, payload.budgets, { it.id }) { it.toEntity().toDomain() }
                readable(BackupEntity.RECURRENCE, payload.recurrences, { it.id }) { it.toEntity().toDomain() }
            }
            return problems
        }
    }
}

class BackupVersionException(val fileVersion: Int, val appVersion: Int) :
    IllegalStateException("Backup schema version $fileVersion is newer than app's $appVersion")

/** Файл бэкапа синтаксически валиден, но нарушает целостность (F1.4). */
class BackupInvalidException(val problems: List<BackupProblem>) :
    IllegalStateException("Backup payload failed validation: ${problems.joinToString("; ")}")

// --- entity <-> backup DTO ---

internal fun CurrencyEntity.toBackup() = CurrencyBackup(code, minorUnits, displayScale, symbol, isActive, displayOrder)
internal fun CurrencyBackup.toEntity() = CurrencyEntity(code, minorUnits, displayScale, symbol, isActive, displayOrder)

internal fun AccountEntity.toBackup() = AccountBackup(
    id, name, currencyCode, kind.name, openingBalanceMinor, color, icon, displayOrder, isArchived, includeInTotal,
)
internal fun AccountBackup.toEntity() = AccountEntity(
    id, name, currencyCode, AccountKind.valueOf(kind), openingBalanceMinor, color, icon, displayOrder, isArchived, includeInTotal,
)

internal fun CategoryEntity.toBackup() = CategoryBackup(
    id, name, kind.name, parentId, color, icon, origin.name, displayOrder, isArchived, importBatchId,
)
internal fun CategoryBackup.toEntity() = CategoryEntity(
    id, name, CategoryKind.valueOf(kind), parentId, color, icon,
    CategoryOrigin.valueOf(origin), displayOrder, isArchived, importBatchId,
)

internal fun TxnEntity.toBackup() = TxnBackup(
    id, kind.name, date, createdAt, updatedAt, accountId, amountMinor, currencyCode,
    toAccountId, toAmountMinor, toCurrencyCode, categoryId, note, importBatchId, importHash,
)
internal fun TxnBackup.toEntity() = TxnEntity(
    id = id, kind = TxnKind.valueOf(kind), date = date, createdAt = createdAt, updatedAt = updatedAt,
    accountId = accountId, amountMinor = amountMinor, currencyCode = currencyCode,
    toAccountId = toAccountId, toAmountMinor = toAmountMinor, toCurrencyCode = toCurrencyCode,
    categoryId = categoryId, note = note, importBatchId = importBatchId, importHash = importHash,
)

internal fun ImportBatchEntity.toBackup() = ImportBatchBackup(id, sourceApp, fileName, importedAt, rowCount, reportJson)
internal fun ImportBatchBackup.toEntity() = ImportBatchEntity(id, sourceApp, fileName, importedAt, rowCount, reportJson)

internal fun ImportAliasEntity.toBackup() = ImportAliasBackup(sourceApp, kind.name, sourceValue, targetId)
internal fun ImportAliasBackup.toEntity() = ImportAliasEntity(sourceApp, ImportAliasKind.valueOf(kind), sourceValue, targetId)

internal fun AppSettingEntity.toBackup() = AppSettingBackup(key, value)
internal fun AppSettingBackup.toEntity() = AppSettingEntity(key, value)

internal fun BudgetEntity.toBackup() = BudgetBackup(id, categoryId, currencyCode, amountMinor, period.name, startsOn)
internal fun BudgetBackup.toEntity() = BudgetEntity(id, categoryId, currencyCode, amountMinor, BudgetPeriod.valueOf(period), startsOn)

internal fun RecurrenceEntity.toBackup() = RecurrenceBackup(
    id, kind.name, accountId, categoryId, amountMinor, currencyCode, note,
    ruleType.name, dayOfMonth, intervalDays, nextRunOn, lastCreatedTxnId,
)
internal fun RecurrenceBackup.toEntity() = RecurrenceEntity(
    id = id, kind = TxnKind.valueOf(kind), accountId = accountId, categoryId = categoryId,
    amountMinor = amountMinor, currencyCode = currencyCode, note = note,
    ruleType = RecurrenceRuleType.valueOf(ruleType), dayOfMonth = dayOfMonth, intervalDays = intervalDays,
    nextRunOn = nextRunOn, lastCreatedTxnId = lastCreatedTxnId,
)
