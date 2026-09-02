package com.corriente.data.backup

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
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверяет то же самое, что BUILD_PLAN.md T1.9 требует от реального BackupRepository
 * ("round-trip тест: экспорт → очистка → импорт → данные идентичны"), но без Room:
 * entity -> BackupDto -> JSON -> BackupDto -> entity, побайтово теми же функциями маппинга,
 * что в core/data/src/main/kotlin/com/corriente/data/backup/BackupRepository.kt.
 */
class BackupRoundTripTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun `full payload survives a JSON round trip byte-for-byte in every entity`() {
        val currency = CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0)
        val account = AccountEntity(
            id = "acc-1", name = "Cash", currencyCode = "RUB", kind = AccountKind.CASH,
            openingBalanceMinor = 1_000_00, color = 0xFF0000, icon = "wallet",
        )
        val category = CategoryEntity(
            id = "cat-1", name = "Дом, ремонт", kind = CategoryKind.EXPENSE, parentId = null,
            color = 0x00FF00, icon = null, origin = CategoryOrigin.IMPORT,
        )
        val expense = TxnEntity(
            id = "txn-1", kind = TxnKind.EXPENSE, date = "2026-03-05", createdAt = 1L, updatedAt = 2L,
            accountId = "acc-1", amountMinor = 12_50, currencyCode = "RUB", categoryId = "cat-1",
            note = "с запятой, и \"кавычками\"",
        )
        val transfer = TxnEntity(
            id = "txn-2", kind = TxnKind.TRANSFER, date = "2026-03-06", createdAt = 3L, updatedAt = 4L,
            accountId = "acc-1", amountMinor = 8_695_00, currencyCode = "RUB",
            toAccountId = "acc-2", toAmountMinor = 100_00, toCurrencyCode = "USD",
        )
        val importBatch = ImportBatchEntity("batch-1", "MONEFY", "export.csv", 5L, 2, "{\"needsReview\":2}")
        val alias = ImportAliasEntity("MONEFY", ImportAliasKind.CATEGORY, "Ремонт и быт", "cat-1")
        val setting = AppSettingEntity("base_currency", "USD")

        val payload = BackupPayload(
            schemaVersion = 1,
            exportedAt = 42L,
            currencies = listOf(currency.toBackup()),
            accounts = listOf(account.toBackup()),
            categories = listOf(category.toBackup()),
            transactions = listOf(expense.toBackup(), transfer.toBackup()),
            importBatches = listOf(importBatch.toBackup()),
            importAliases = listOf(alias.toBackup()),
            appSettings = listOf(setting.toBackup()),
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)

        assertEquals(currency, decoded.currencies.single().toEntity())
        assertEquals(account, decoded.accounts.single().toEntity())
        assertEquals(category, decoded.categories.single().toEntity())
        assertEquals(listOf(expense, transfer), decoded.transactions.map { it.toEntity() })
        assertEquals(importBatch, decoded.importBatches.single().toEntity())
        assertEquals(alias, decoded.importAliases.single().toEntity())
        assertEquals(setting, decoded.appSettings.single().toEntity())
        assertEquals(payload, decoded)
    }

    // F1.4 — проверка целостности до записи.
    private fun payloadOf(vararg txns: TxnEntity) = BackupPayload(
        schemaVersion = 1, exportedAt = 0,
        currencies = listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0).toBackup()),
        accounts = listOf(
            AccountEntity("acc", "Наличные", "RUB", AccountKind.CASH, 0, 0).toBackup(),
        ),
        categories = listOf(CategoryEntity("cat", "Еда", CategoryKind.EXPENSE, color = 0).toBackup()),
        transactions = txns.map { it.toBackup() },
        importBatches = emptyList(), importAliases = emptyList(), appSettings = emptyList(),
    )

    private fun expenseEntity(id: String) = TxnEntity(
        id = id, kind = TxnKind.EXPENSE, date = "2026-01-01", createdAt = 0, updatedAt = 0,
        accountId = "acc", amountMinor = 100, currencyCode = "RUB", categoryId = "cat",
    )

    @Test
    fun `validate returns no problems for a consistent payload`() {
        assertEquals(emptyList<String>(), BackupRepository.validate(payloadOf(expenseEntity("t1"))))
    }

    @Test
    fun `validate flags a dangling account reference`() {
        val problems = BackupRepository.validate(payloadOf(expenseEntity("t1").copy(accountId = "ghost")))
        assertEquals(1, problems.size)
        assert(problems.single().contains("ghost")) { problems.toString() }
    }

    @Test
    fun `validate flags a non-positive amount, an unknown currency and a bad date`() {
        val bad = expenseEntity("t1").copy(amountMinor = 0, currencyCode = "ZZZ", date = "not-a-date")
        val problems = BackupRepository.validate(payloadOf(bad))
        assertEquals(3, problems.size)
    }

    @Test
    fun `validate flags a transfer missing its second side and one carrying a category`() {
        val halfTransfer = TxnEntity(
            id = "tr", kind = TxnKind.TRANSFER, date = "2026-01-01", createdAt = 0, updatedAt = 0,
            accountId = "acc", amountMinor = 100, currencyCode = "RUB", categoryId = "cat",
        )
        val problems = BackupRepository.validate(payloadOf(halfTransfer))
        assert(problems.any { it.contains("вторая сторона") }) { problems.toString() }
        assert(problems.any { it.contains("категори") }) { problems.toString() }
    }

    // Отдельно - именно та особенность, из-за которой перевод вообще существует одной строкой
    // с nullable-полями (I-7а): они обязаны пережить сериализацию как null, а не как "0"/"".
    @Test
    fun `nullable transfer fields round-trip as null for a plain expense`() {
        val expense = TxnEntity(
            id = "txn-1", kind = TxnKind.EXPENSE, date = "2026-01-01", createdAt = 0, updatedAt = 0,
            accountId = "acc-1", amountMinor = 100, currencyCode = "RUB", categoryId = null,
        )
        val encoded = json.encodeToString(TxnBackup.serializer(), expense.toBackup())
        val decoded = json.decodeFromString(TxnBackup.serializer(), encoded).toEntity()
        assertEquals(null, decoded.toAccountId)
        assertEquals(null, decoded.toAmountMinor)
        assertEquals(null, decoded.toCurrencyCode)
        assertEquals(null, decoded.categoryId)
        assertEquals(expense, decoded)
    }
}
