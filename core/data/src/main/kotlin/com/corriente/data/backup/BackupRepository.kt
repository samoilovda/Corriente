package com.corriente.data.backup

import androidx.room.withTransaction
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.ImportAliasEntity
import com.corriente.data.db.entity.ImportAliasKind
import com.corriente.data.db.entity.ImportBatchEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/**
 * Полный экспорт/восстановление БД в файл (T1.9, I-21). Пишется и читается через SAF —
 * вызывающий код (:app) сам открывает поток на выбранный пользователем файл, репозиторий
 * знает только про [InputStream]/[OutputStream], а не про `Uri`/`ContentResolver` (I-24 заодно:
 * это гарантирует, что бэкап не превратится незаметно в "заодно и в облако").
 *
 * Восстановление **полностью замещает** текущие данные — экран T1.9 обязан спросить
 * подтверждение до вызова [restore].
 */
class BackupRepository(private val db: AppDatabase) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(output: OutputStream) {
        val payload = BackupPayload(
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
        )
        output.writer(Charsets.UTF_8).use { it.write(json.encodeToString(BackupPayload.serializer(), payload)) }
    }

    /**
     * @throws BackupVersionException если версия схемы файла новее той, что умеет читать
     * текущая версия приложения (I-21: старые версии обязаны читаться через миграции, а не
     * этот путь; более новые читать безопасно нельзя в принципе).
     */
    suspend fun restore(input: InputStream, beforeReplace: suspend () -> Unit = {}) {
        val payload = input.reader(Charsets.UTF_8).use {
            json.decodeFromString(BackupPayload.serializer(), it.readText())
        }
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
            db.txnDao().deleteAll()
            db.importBatchDao().deleteAll()
            db.importAliasDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.appSettingDao().deleteAll()
            db.currencyDao().deleteAll()

            // И обратно - от независимых к зависимым.
            db.currencyDao().insertAll(payload.currencies.map { it.toEntity() })
            payload.accounts.forEach { db.accountDao().insert(it.toEntity()) }
            payload.categories.forEach { db.categoryDao().insert(it.toEntity()) }
            db.txnDao().insertAll(payload.transactions.map { it.toEntity() })
            payload.importBatches.forEach { db.importBatchDao().insert(it.toEntity()) }
            payload.importAliases.forEach { db.importAliasDao().upsert(it.toEntity()) }
            payload.appSettings.forEach { db.appSettingDao().set(it.toEntity()) }
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * Проверка полезной нагрузки бэкапа до записи (F1.4). Чистая функция — тестируется без БД.
         * @return список человекочитаемых проблем; пустой — файл можно восстанавливать.
         */
        fun validate(payload: BackupPayload): List<String> {
            val problems = mutableListOf<String>()
            val currencyCodes = payload.currencies.mapTo(HashSet()) { it.code }
            val accountIds = payload.accounts.mapTo(HashSet()) { it.id }
            val categoryIds = payload.categories.mapTo(HashSet()) { it.id }
            val batchIds = payload.importBatches.mapTo(HashSet()) { it.id }

            payload.accounts.forEach { a ->
                if (a.currencyCode !in currencyCodes) {
                    problems += "счёт «${a.name}»: валюты ${a.currencyCode} нет в справочнике"
                }
            }
            payload.categories.forEach { c ->
                if (c.parentId != null && c.parentId !in categoryIds) {
                    problems += "категория «${c.name}»: родитель ${c.parentId} не найден"
                }
            }
            payload.transactions.forEach { t ->
                val tag = "операция ${t.id}"
                if (t.accountId !in accountIds) problems += "$tag: счёт ${t.accountId} не найден"
                if (t.currencyCode !in currencyCodes) problems += "$tag: валюты ${t.currencyCode} нет в справочнике"
                if (t.categoryId != null && t.categoryId !in categoryIds) {
                    problems += "$tag: категория ${t.categoryId} не найдена"
                }
                if (t.importBatchId != null && t.importBatchId !in batchIds) {
                    problems += "$tag: батч импорта ${t.importBatchId} не найден"
                }
                if (t.amountMinor <= 0) problems += "$tag: сумма ${t.amountMinor} не положительна"
                if (runCatching { LocalDate.parse(t.date) }.isFailure) {
                    problems += "$tag: дата «${t.date}» не разбирается"
                }
                when (t.kind) {
                    "TRANSFER" -> {
                        if (t.toAccountId == null || t.toAmountMinor == null || t.toCurrencyCode == null) {
                            problems += "$tag: у перевода не заполнена вторая сторона"
                        } else {
                            if (t.toAccountId !in accountIds) problems += "$tag: счёт-получатель ${t.toAccountId} не найден"
                            if (t.toCurrencyCode !in currencyCodes) problems += "$tag: валюты получателя ${t.toCurrencyCode} нет"
                            if (t.toAmountMinor <= 0) problems += "$tag: сумма получателя ${t.toAmountMinor} не положительна"
                        }
                        if (t.categoryId != null) problems += "$tag: у перевода не должно быть категории"
                    }

                    "EXPENSE", "INCOME" -> if (
                        t.toAccountId != null || t.toAmountMinor != null || t.toCurrencyCode != null
                    ) {
                        problems += "$tag: у расхода/дохода не должно быть второй стороны"
                    }

                    else -> problems += "$tag: неизвестный тип «${t.kind}»"
                }
            }
            return problems
        }
    }
}

class BackupVersionException(val fileVersion: Int, val appVersion: Int) :
    IllegalStateException("Backup schema version $fileVersion is newer than app's $appVersion")

/** Файл бэкапа синтаксически валиден, но нарушает целостность (F1.4). */
class BackupInvalidException(val problems: List<String>) :
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
    id, name, kind.name, parentId, color, icon, origin.name, displayOrder, isArchived,
)
internal fun CategoryBackup.toEntity() = CategoryEntity(
    id, name, CategoryKind.valueOf(kind), parentId, color, icon, CategoryOrigin.valueOf(origin), displayOrder, isArchived,
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
