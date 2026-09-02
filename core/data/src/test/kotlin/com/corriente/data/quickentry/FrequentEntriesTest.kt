package com.corriente.data.quickentry

import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R2.2 — ранжирование шаблонов быстрого ввода. */
class FrequentEntriesTest {

    private val rub = CurrencyCode("RUB")
    private val today = LocalDate.of(2026, 9, 2)

    private fun expense(date: LocalDate, accountId: String, categoryId: String?, amountMinor: Long) =
        Txn.Expense("e-${date}-$accountId-$categoryId-$amountMinor-${(0..999999).random()}", date, 0, 0, accountId, Money(Minor(amountMinor), rub), categoryId)

    private fun income(date: LocalDate, accountId: String, categoryId: String?, amountMinor: Long) =
        Txn.Income("i-${date}-$accountId-$categoryId-$amountMinor-${(0..999999).random()}", date, 0, 0, accountId, Money(Minor(amountMinor), rub), categoryId)

    @Test
    fun `ranks by occurrence count, most frequent first`() {
        val txns = buildList {
            repeat(5) { add(expense(today.minusDays(it.toLong()), "cash", "food", 30000)) }
            repeat(3) { add(expense(today.minusDays(it.toLong()), "card", "transport", 5000)) }
        }
        val result = frequentEntries(txns, today, limit = 5)
        assertEquals(2, result.size)
        assertEquals(FrequentEntry(FrequentEntryKind.EXPENSE, "cash", "food", 30000, "RUB"), result[0])
        assertEquals(FrequentEntry(FrequentEntryKind.EXPENSE, "card", "transport", 5000, "RUB"), result[1])
    }

    @Test
    fun `ties broken by most recent last use`() {
        val txns = listOf(
            expense(today.minusDays(10), "cash", "food", 10000),
            expense(today.minusDays(9), "cash", "food", 10000),
            expense(today.minusDays(1), "card", "taxi", 20000),
            expense(today, "card", "taxi", 20000),
        )
        val result = frequentEntries(txns, today, limit = 5)
        assertEquals(FrequentEntry(FrequentEntryKind.EXPENSE, "card", "taxi", 20000, "RUB"), result[0])
    }

    @Test
    fun `limit caps the result size`() {
        val txns = (1..4).flatMap { i ->
            (1..3).map { expense(today.minusDays(it.toLong()), "acc$i", "cat$i", i * 1000L) }
        }
        assertEquals(2, frequentEntries(txns, today, limit = 2).size)
    }

    @Test
    fun `single occurrence is not frequent yet`() {
        val txns = listOf(expense(today, "cash", "food", 30000))
        assertTrue(frequentEntries(txns, today, limit = 5).isEmpty())
    }

    @Test
    fun `operations outside the window are ignored`() {
        val insideWindow = expense(today.minusDays(10), "cash", "food", 30000)
        val outsideWindow = expense(today.minusDays(200), "cash", "food", 30000)
        val txns = listOf(insideWindow, outsideWindow, insideWindow.copy(id = "e2"))
        val result = frequentEntries(txns, today, limit = 5, windowDays = 90)
        // Только 2 внутри окна — этого достаточно для minOccurrences=2, но старая операция
        // (200 дней назад) не должна учитываться в счётчике.
        assertEquals(1, result.size)

        val onlyOldOnes = listOf(outsideWindow, outsideWindow.copy(id = "old2"))
        assertTrue(frequentEntries(onlyOldOnes, today, limit = 5, windowDays = 90).isEmpty())
    }

    // I-11: перевод не входит ни в отчёты, ни в шаблоны быстрого ввода.
    @Test
    fun `transfers are excluded from templates`() {
        val transfer = Txn.Transfer(
            "t1", today, 0, 0, "cash", Money(Minor(50000), rub), "card", Money(Minor(50000), rub),
        )
        val txns = List(5) { transfer.copy(id = "t$it") }
        assertTrue(frequentEntries(txns, today, limit = 5).isEmpty())
    }

    @Test
    fun `expense and income with the same amount and account are different templates`() {
        val txns = buildList {
            repeat(2) { add(expense(today.minusDays(it.toLong()), "cash", "food", 10000)) }
            repeat(2) { add(income(today.minusDays(it.toLong()), "cash", "food", 10000)) }
        }
        val result = frequentEntries(txns, today, limit = 5)
        assertEquals(2, result.size)
        assertTrue(result.any { it.kind == FrequentEntryKind.EXPENSE })
        assertTrue(result.any { it.kind == FrequentEntryKind.INCOME })
    }

    @Test
    fun `zero or negative limit returns empty`() {
        val txns = buildList { repeat(5) { add(expense(today, "cash", "food", 10000)) } }
        assertTrue(frequentEntries(txns, today, limit = 0).isEmpty())
    }
}
