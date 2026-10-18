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

    // R2.1: область поиска расширена — заметка/категория/счёт (не только заметка и категория).
    @Test
    fun `query also matches account name`() {
        assertEquals(listOf("e2", "e3"), ids(TxnFilter(query = "card")))
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

    // U01: порог привязан к валюте — 20.00 USD не попадает под «до 250.00 RUB».
    @Test
    fun `amount range compares magnitude only within its currency`() {
        assertEquals(listOf("e2", "i1"), ids(TxnFilter(amount = AmountRange("RUB", minMinor = 100_000))))
        assertEquals(listOf("e1"), ids(TxnFilter(amount = AmountRange("RUB", maxMinor = 25_000))))
        assertEquals(listOf("e3"), ids(TxnFilter(amount = AmountRange("USD", maxMinor = 25_000))))
    }

    @Test
    fun `transfer matches when one side in the range currency fits both bounds`() {
        val transfer = Txn.Transfer(
            "t", LocalDate.of(2026, 3, 2), 0, 0, "cash", Money(Minor(500_000), rub), "card", Money(Minor(5_000), usd),
        )
        fun matches(range: AmountRange) = buildDaySections(listOf(transfer), TxnFilter(amount = range), accountNames, categoryNames, byCode)
            .isNotEmpty()
        assertEquals(true, matches(AmountRange("USD", minMinor = 4_000, maxMinor = 6_000)))
        // RUB-сторона 5000.00 не подходит под «≤ 60.00» — то, что USD-сторона «50.00» подошла бы, не важно.
        assertEquals(false, matches(AmountRange("RUB", maxMinor = 6_000)))
        assertEquals(true, matches(AmountRange("RUB", minMinor = 500_000, maxMinor = 500_000)))
    }

    @Test
    fun `long upper bound does not overflow`() {
        assertEquals(listOf("e1", "e2", "i1"), ids(TxnFilter(amount = AmountRange("RUB", minMinor = 0, maxMinor = Long.MAX_VALUE))))
    }

    @Test
    fun `filters combine with AND`() {
        assertEquals(listOf("e1"), ids(TxnFilter(categoryId = "food", currencyCode = "RUB", query = "маг")))
    }

    // R3.0: маркер счёта в строке операции не зависит от категории — берётся по accountId.
    @Test
    fun `row carries account color regardless of category`() {
        val accountColors = mapOf("cash" to 0x11223344, "card" to 0x55667788)
        val sections = buildDaySections(
            txns, TxnFilter(), accountNames, categoryNames, byCode, accountColors = accountColors,
        )
        val byId = sections.flatMap { it.rows }.associateBy { it.id }
        assertEquals(0x11223344.toInt(), byId.getValue("e1").accountColor)
        assertEquals(0x55667788.toInt(), byId.getValue("e2").accountColor)
    }

    @Test
    fun `row without a known account color falls back to zero`() {
        val sections = buildDaySections(txns, TxnFilter(), accountNames, categoryNames, byCode)
        assertEquals(0, sections.flatMap { it.rows }.first().accountColor)
    }
}
