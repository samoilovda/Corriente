package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CurrencyTotalsTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val eur = CurrencyCode("EUR")
    private val march = LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31)
    private val day = LocalDate.of(2026, 3, 10)

    private fun money(minor: Long, code: CurrencyCode) = Money(Minor(minor), code)
    private fun expense(id: String, minor: Long, code: CurrencyCode, date: LocalDate = day) =
        Txn.Expense(id, date, 0, 0, "acc-${code.code}", money(minor, code), null)
    private fun income(id: String, minor: Long, code: CurrencyCode, date: LocalDate = day) =
        Txn.Income(id, date, 0, 0, "acc-${code.code}", money(minor, code), null)

    @Test
    fun `суммы по каждой валюте отдельно, без пересчёта`() {
        val totals = currencyTotals(
            listOf(
                expense("e1", 1_000_00, rub),
                expense("e2", 500_00, rub),
                income("i1", 50_000_00, rub),
                expense("e3", 20_00, usd),
                income("i2", 300_00, usd),
            ),
            march,
        ).associateBy { it.currency }

        assertEquals(CurrencyTotals(rub, money(50_000_00, rub), money(1_500_00, rub), 1, 2), totals.getValue(rub))
        assertEquals(CurrencyTotals(usd, money(300_00, usd), money(20_00, usd), 1, 1), totals.getValue(usd))
    }

    @Test
    fun `валюта только с расходами - доход нулевой в той же валюте`() {
        val totals = currencyTotals(listOf(expense("e1", 700, eur)), march)
        assertEquals(listOf(CurrencyTotals(eur, Money.zero(eur), money(700, eur), 0, 1)), totals)
    }

    @Test
    fun `переводы, корректировки и операции вне периода не входят`() {
        val totals = currencyTotals(
            listOf(
                Txn.Transfer("t1", day, 0, 0, "a", money(100_00, rub), "b", money(1_00, usd)),
                Txn.Adjustment("a1", day, 0, 0, "a", money(5_00, rub)),
                expense("old", 999, rub, date = LocalDate.of(2026, 2, 28)),
                income("next", 999, usd, date = LocalDate.of(2026, 4, 1)),
            ),
            march,
        )
        assertTrue(totals.isEmpty())
    }

    @Test
    fun `порядок - по числу операций, при равенстве по коду`() {
        val totals = currencyTotals(
            listOf(
                expense("u1", 1, usd),
                expense("e1", 1, eur),
                expense("r1", 1, rub), income("r2", 1, rub),
            ),
            march,
        )
        assertEquals(listOf(rub, eur, usd), totals.map { it.currency })
    }
}
