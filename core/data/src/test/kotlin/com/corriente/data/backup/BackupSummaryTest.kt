package com.corriente.data.backup

import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** R1.4: BackupRepository.summarize — чистая функция над BackupPayload, без БД. */
class BackupSummaryTest {

    private fun payloadOf(vararg txns: TxnEntity) = BackupPayload(
        schemaVersion = 1, exportedAt = 0,
        currencies = listOf(
            CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0).toBackup(),
            CurrencyEntity("USD", 2, 2, "$", isActive = true, displayOrder = 1).toBackup(),
        ),
        accounts = listOf(
            AccountEntity("acc-rub", "Наличные", "RUB", AccountKind.CASH, 0, 0).toBackup(),
            AccountEntity("acc-usd", "Card", "USD", AccountKind.CARD, 0, 0).toBackup(),
        ),
        categories = listOf(CategoryEntity("cat", "Еда", CategoryKind.EXPENSE, color = 0).toBackup()),
        transactions = txns.map { it.toBackup() },
        importBatches = emptyList(), importAliases = emptyList(), appSettings = emptyList(),
    )

    @Test
    fun `counts accounts categories and transactions`() {
        val payload = payloadOf(
            TxnEntity("t1", TxnKind.EXPENSE, "2026-01-01", 0, 0, "acc-rub", 100, "RUB", categoryId = "cat"),
        )
        val summary = BackupRepository.summarize(payload)
        assertEquals(2, summary.accounts)
        assertEquals(1, summary.categories)
        assertEquals(1, summary.transactions)
    }

    @Test
    fun `sums income and expense per currency and excludes transfers`() {
        val expense = TxnEntity("t1", TxnKind.EXPENSE, "2026-01-01", 0, 0, "acc-rub", 300, "RUB", categoryId = "cat")
        val income = TxnEntity("t2", TxnKind.INCOME, "2026-01-02", 0, 0, "acc-rub", 1000, "RUB", categoryId = "cat")
        val usdExpense = TxnEntity("t3", TxnKind.EXPENSE, "2026-01-03", 0, 0, "acc-usd", 50, "USD", categoryId = "cat")
        val transfer = TxnEntity(
            "t4", TxnKind.TRANSFER, "2026-01-04", 0, 0, "acc-rub", 999_999, "RUB",
            toAccountId = "acc-usd", toAmountMinor = 10_000, toCurrencyCode = "USD",
        )

        val summary = BackupRepository.summarize(payloadOf(expense, income, usdExpense, transfer))

        // I-11: перевод не входит ни в доходы, ни в расходы — не искажает сумму по RUB.
        assertEquals(mapOf("RUB" to 700L, "USD" to -50L), summary.sumsByCurrency)
        assertEquals(4, summary.transactions)
    }

    @Test
    fun `empty payload has zero counts and no currency sums`() {
        val summary = BackupRepository.summarize(payloadOf())
        assertEquals(0, summary.transactions)
        assertEquals(emptyMap<String, Long>(), summary.sumsByCurrency)
    }
}
