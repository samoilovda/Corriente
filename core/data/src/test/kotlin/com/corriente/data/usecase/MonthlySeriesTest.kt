package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** T5.3: ряд расходов по месяцам — одна валюта, переводы вне ряда, пустые месяцы = 0. */
class MonthlySeriesTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val anchor = LocalDate.of(2026, 3, 15)

    private fun expense(date: LocalDate, minor: Long, cur: CurrencyCode = rub) =
        Txn.Expense("e$date$minor", date, 0, 0, "a", Money(Minor(minor), cur), null)

    @Test
    fun `three-month window, empty months are zero, other currency ignored`() {
        val txns = listOf(
            expense(LocalDate.of(2026, 1, 10), 100_00),
            expense(LocalDate.of(2026, 1, 20), 50_00),
            expense(LocalDate.of(2026, 3, 1), 200_00),
            expense(LocalDate.of(2026, 3, 2), 9_99, usd),      // другая валюта
            expense(LocalDate.of(2025, 12, 31), 777_00),       // вне окна
        )
        val series = monthlySeries(txns, rub, ReportKind.EXPENSE, anchor, monthsBack = 3)

        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            series.map { it.month },
        )
        assertEquals(listOf(150_00L, 0L, 200_00L), series.map { it.total.amount.raw })
        assertEquals(rub, series.first().total.currency)
    }

    @Test
    fun `transfers never contribute`() {
        val txns = listOf(
            Txn.Transfer("t", LocalDate.of(2026, 3, 5), 0, 0, "a", Money(Minor(500_00), rub), "b", Money(Minor(500_00), rub)),
        )
        assertEquals(listOf(0L), monthlySeries(txns, rub, ReportKind.EXPENSE, anchor, 1).map { it.total.amount.raw })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `monthsBack must be positive`() {
        monthlySeries(emptyList(), rub, ReportKind.EXPENSE, anchor, 0)
    }
}
