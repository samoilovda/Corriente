package com.corriente.data.usecase

import com.corriente.data.db.entity.BudgetPeriod
import com.corriente.data.model.Budget
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R2.3 — расчёт «потрачено из бюджета» над уже готовым categoryReport, без БД. */
class BudgetProgressTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val starts = LocalDate.of(2026, 9, 1)

    private fun budget(categoryId: String?, amountMinor: Long, currency: CurrencyCode = rub) = Budget(
        id = "b-$categoryId-$amountMinor", categoryId = categoryId,
        amount = Money(Minor(amountMinor), currency), period = BudgetPeriod.MONTH, startsOn = starts,
    )

    private fun total(categoryId: String?, amountMinor: Long, currency: CurrencyCode = rub) =
        CategoryTotal(categoryId, Money(Minor(amountMinor), currency))

    @Test
    fun `spending within budget`() {
        val result = budgetProgress(listOf(budget("food", 10_000_00)), listOf(total("food", 3_000_00)), rub)
        val p = result.single()
        assertEquals(3_000_00, p.spent.amount.raw)
        assertEquals(7_000_00, p.remaining.amount.raw)
        assertEquals(30, p.percent)
        assertFalse(p.isOverBudget)
    }

    @Test
    fun `overshoot is flagged and remaining goes negative`() {
        val result = budgetProgress(listOf(budget("food", 10_000_00)), listOf(total("food", 15_000_00)), rub)
        val p = result.single()
        assertEquals(-5_000_00, p.remaining.amount.raw)
        assertEquals(150, p.percent)
        assertTrue(p.isOverBudget)
    }

    @Test
    fun `zero budget has no percent but still flags overshoot when anything was spent`() {
        val zeroWithSpend = budgetProgress(listOf(budget("food", 0)), listOf(total("food", 100_00)), rub).single()
        assertNull(zeroWithSpend.percent)
        assertTrue(zeroWithSpend.isOverBudget)

        val zeroNoSpend = budgetProgress(listOf(budget("food", 0)), emptyList(), rub).single()
        assertNull(zeroNoSpend.percent)
        assertFalse(zeroNoSpend.isOverBudget)
    }

    @Test
    fun `period with no transactions means fully unspent`() {
        val p = budgetProgress(listOf(budget("food", 5_000_00)), emptyList(), rub).single()
        assertEquals(0L, p.spent.amount.raw)
        assertEquals(5_000_00, p.remaining.amount.raw)
        assertEquals(0, p.percent)
    }

    @Test
    fun `whole-currency budget sums every category`() {
        val report = listOf(total("food", 1_000_00), total("taxi", 500_00))
        val p = budgetProgress(listOf(budget(null, 2_000_00)), report, rub).single()
        assertEquals(1_500_00, p.spent.amount.raw)
    }

    // ADR-012/I-8: бюджеты разных валют не смешиваются — бюджет в другой валюте не участвует
    // в расчёте по этой валюте вовсе (это не 0%, а "не относится").
    @Test
    fun `budgets in a different currency are excluded, not zeroed`() {
        val result = budgetProgress(
            listOf(budget("food", 10_000_00, rub), budget("food", 100_00, usd)),
            listOf(total("food", 3_000_00, rub)),
            rub,
        )
        assertEquals(1, result.size)
        assertEquals(rub, result.single().budget.amount.currency)
    }

    @Test
    fun `category budget only sees its own category, not the whole report`() {
        val report = listOf(total("food", 1_000_00), total("taxi", 9_000_00))
        val p = budgetProgress(listOf(budget("food", 2_000_00)), report, rub).single()
        assertEquals(1_000_00, p.spent.amount.raw)
    }
}
