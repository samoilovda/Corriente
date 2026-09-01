package com.corriente.app.ui.transactions

import com.corriente.data.model.Txn
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** T5.2: поиск и фильтры по операциям — чистая логика [buildDaySections]. */
class TxnFilterTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val byCode = mapOf("RUB" to Currency(rub, 2, 2, "₽"), "USD" to Currency(usd, 2, 2, "$"))
    private val accountNames = mapOf("cash" to "Cash", "card" to "Card")
    private val categoryNames = mapOf("food" to "Продукты", "taxi" to "Такси")

    private val txns = listOf(
        Txn.Expense("e1", LocalDate.of(2026, 3, 1), 0, 0, "cash", Money(Minor(25_000), rub), "food", note = "магнит"),
        Txn.Expense("e2", LocalDate.of(2026, 3, 5), 0, 0, "card", Money(Minor(120_000), rub), "taxi", note = null),
        Txn.Income("i1", LocalDate.of(2026, 3, 10), 0, 0, "cash", Money(Minor(5_000_000), rub), null, note = "зарплата"),
        Txn.Expense("e3", LocalDate.of(2026, 3, 12), 0, 0, "card", Money(Minor(2_000), usd), "food", note = null),
    )

    private fun ids(filter: TxnFilter): List<String> =
        buildDaySections(txns, filter, accountNames, categoryNames, byCode)
            .flatMap { it.rows }.map { it.id }.sorted()

    @Test
    fun `no filter returns everything`() {
        assertEquals(listOf("e1", "e2", "e3", "i1"), ids(TxnFilter()))
    }

    @Test
    fun `query matches note or category name, case-insensitive`() {
        assertEquals(listOf("e1"), ids(TxnFilter(query = "МАГ")))
        assertEquals(listOf("e1", "e3"), ids(TxnFilter(query = "продукт")))
        assertEquals(listOf("i1"), ids(TxnFilter(query = "зарплата")))
    }

    @Test
    fun `category, account and currency filters`() {
        assertEquals(listOf("e1", "e3"), ids(TxnFilter(categoryId = "food")))
        assertEquals(listOf("e2", "e3"), ids(TxnFilter(accountId = "card")))
        assertEquals(listOf("e3"), ids(TxnFilter(currencyCode = "USD")))
    }

    @Test
    fun `period filter is inclusive`() {
        assertEquals(
            listOf("e2", "i1"),
            ids(TxnFilter(from = LocalDate.of(2026, 3, 5), to = LocalDate.of(2026, 3, 10))),
        )
    }

    @Test
    fun `amount range compares magnitude across currencies`() {
        assertEquals(listOf("e2", "i1"), ids(TxnFilter(minAmountMinor = 100_000)))
        assertEquals(listOf("e1", "e3"), ids(TxnFilter(maxAmountMinor = 25_000)))
    }

    @Test
    fun `filters combine with AND`() {
        assertEquals(listOf("e1"), ids(TxnFilter(categoryId = "food", currencyCode = "RUB", query = "маг")))
    }
}
