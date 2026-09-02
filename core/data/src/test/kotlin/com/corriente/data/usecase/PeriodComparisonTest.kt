package com.corriente.data.usecase

import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R3.1: сравнение суммы категории с тем же периодом ранее — внутри одной валюты (I-8). */
class PeriodComparisonTest {

    private val rub = CurrencyCode("RUB")

    private fun total(categoryId: String?, minor: Long) = CategoryTotal(categoryId, Money(Minor(minor), rub))

    @Test
    fun `increase and decrease compute an exact integer percent`() {
        val current = listOf(total("food", 11_800_00), total("taxi", 900_00))
        val previous = listOf(total("food", 10_000_00), total("taxi", 1_000_00))
        val result = periodOverPeriodChange(current, previous)
        // (1180000 - 1000000) * 100 / 1000000 = 18
        assertEquals(18, result.getValue("food"))
        // (90000 - 100000) * 100 / 100000 = -10
        assertEquals(-10, result.getValue("taxi"))
    }

    @Test
    fun `a percent that is not exact truncates towards zero`() {
        // (100 - 70) * 100 / 70 = 42.857... -> 42, not 43 (round-half-up would over-claim growth).
        assertEquals(42, changePercent(currentRaw = 100L, previousRaw = 70L))
        // symmetric on the negative side: (70 - 100) * 100 / 100 = -30 exactly, pick a non-exact one:
        // (33 - 70) * 100 / 70 = -52.857... -> -52
        assertEquals(-52, changePercent(currentRaw = 33L, previousRaw = 70L))
    }

    @Test
    fun `zero previous amount is a dash, not a 0 percent change`() {
        val current = listOf(total("food", 1_000_00))
        val previous = listOf(total("food", 0))
        assertNull(periodOverPeriodChange(current, previous).getValue("food"))
    }

    @Test
    fun `category with no data at all last period is a dash`() {
        val current = listOf(total("food", 1_000_00))
        val previous = emptyList<CategoryTotal>()
        assertNull(periodOverPeriodChange(current, previous).getValue("food"))
    }

    // Появилась в этом периоде — не было в прошлом вовсе.
    @Test
    fun `a category that appeared this period has no previous entry and is a dash`() {
        val current = listOf(total("food", 500_00), total("new-category", 200_00))
        val previous = listOf(total("food", 500_00))
        val result = periodOverPeriodChange(current, previous)
        assertEquals(0, result.getValue("food"))
        assertNull(result.getValue("new-category"))
    }

    // Категория, исчезнувшая в этом периоде, просто не даёт строки сравнения вовсе — сравнивать
    // нечего для строки, которой нет в текущем отчёте.
    @Test
    fun `a category that disappeared this period is absent from the result entirely`() {
        val current = listOf(total("food", 500_00))
        val previous = listOf(total("food", 500_00), total("gone", 300_00))
        val result = periodOverPeriodChange(current, previous)
        assertEquals(1, result.size)
        assertEquals(setOf("food"), result.keys)
    }

    @Test
    fun `no change is exactly zero, not a dash`() {
        val current = listOf(total("food", 1_000_00))
        val previous = listOf(total("food", 1_000_00))
        assertEquals(0, periodOverPeriodChange(current, previous).getValue("food"))
    }

    @Test
    fun `the uncategorized bucket (null category id) compares like any other`() {
        val current = listOf(total(null, 500_00))
        val previous = listOf(total(null, 400_00))
        // (50000-40000)*100/40000 = 25
        assertEquals(25, periodOverPeriodChange(current, previous).getValue(null))
    }
}
