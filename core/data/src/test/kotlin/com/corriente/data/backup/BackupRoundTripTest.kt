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
