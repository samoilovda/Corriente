package com.corriente.data.usecase

import com.corriente.data.db.entity.BudgetPeriod
import com.corriente.data.model.Budget
import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** U07: обзор бюджетов за месяц — та же арифметика, что у полос отчёта, по каждой валюте отдельно. */
class MonthBudgetProgressTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val october = YearMonth.of(2026, 10)

    private fun budget(id: String, categoryId: String?, minor: Long, currency: CurrencyCode = rub, starts: LocalDate = LocalDate.of(2026, 1, 1)) =
        Budget(id, categoryId, Money(Minor(minor), currency), BudgetPeriod.MONTH, starts)

    private fun expense(id: String, date: LocalDate, minor: Long, category: String?, currency: CurrencyCode = rub) =
        Txn.Expense(id, date, 0, 0, "acc", Money(Minor(minor), currency), category)

    private val txns = listOf(
        expense("e1", LocalDate.of(2026, 10, 1), 300_00, "food"),
        expense("e2", LocalDate.of(2026, 10, 31), 200_00, "taxi"),
        expense("e3", LocalDate.of(2026, 9, 30), 999_00, "food"), // прошлый месяц — не в счёт
        expense("e4", LocalDate.of(2026, 10, 5), 50_00, "food", usd),
        Txn.Income("i1", LocalDate.of(2026, 10, 2), 0, 0, "acc", Money(Minor(5_000_00), rub), null),
        Txn.Transfer("t1", LocalDate.of(2026, 10, 3), 0, 0, "acc", Money(Minor(1_000_00), rub), "acc2", Money(Minor(1_000_00), rub)),
    )

    @Test
    fun `whole and category budgets are separate limits, currencies are separate`() {
        val result = monthBudgetProgress(
            listOf(budget("all", null, 1_000_00), budget("food", "food", 250_00), budget("usd", "food", 40_00, usd)),
            txns,
            october,
        ).associateBy { it.budget.id }

        // Общий: все расходы RUB за октябрь (переводы и доходы не входят) — 500.00 из 1000.00.
        assertEquals(500_00, result.getValue("all").spent.amount.raw)
        assertEquals(500_00, result.getValue("all").remaining.amount.raw)
        // Категория: 300.00 из 250.00 — перерасход 50.00; лимиты не складываются с общим.
        assertEquals(-50_00, result.getValue("food").remaining.amount.raw)
        assertTrue(result.getValue("food").isOverBudget)
        // USD считается отдельно.
        assertEquals(50_00, result.getValue("usd").spent.amount.raw)
        assertEquals(usd, result.getValue("usd").spent.currency)
    }

    @Test
    fun `budget that starts after the month is not shown, one starting on the last day is`() {
        val result = monthBudgetProgress(
            listOf(
                budget("later", null, 100_00, starts = LocalDate.of(2026, 11, 1)),
                budget("lastDay", null, 100_00, starts = LocalDate.of(2026, 10, 31)),
            ),
            txns,
            october,
        )
        assertEquals(listOf("lastDay"), result.map { it.budget.id })
    }

    @Test
    fun `matches the report computation for the same month`() {
        val budgets = listOf(budget("all", null, 1_000_00), budget("food", "food", 250_00))
        val period = october.atDay(1)..october.atEndOfMonth()
        val viaReport = budgetProgress(budgets, categoryReport(txns, rub, period, ReportKind.EXPENSE), rub)
        assertEquals(viaReport, monthBudgetProgress(budgets, txns, october))
    }
}
