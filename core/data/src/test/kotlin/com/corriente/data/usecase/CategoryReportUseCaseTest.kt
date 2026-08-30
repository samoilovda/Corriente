package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Прогнано заранее вне Android/Room в throwaway JVM-гарнитуре (см. итоговое сообщение сессии) —
 * 4/4 зелёных на тех же сценариях. Здесь — для прогона в Android Studio.
 */
class CategoryReportUseCaseTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val march = LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 31)

    private fun expense(id: String, date: LocalDate, categoryId: String?, amount: Long, currency: CurrencyCode = rub) =
        Txn.Expense(id, date, 0, 0, "acc", Money(Minor(amount), currency), categoryId)

    @Test
    fun `groups expenses by category within the period and currency`() {
        val txns = listOf(
            expense("e1", LocalDate.of(2026, 3, 5), "food", 25_000),
            expense("e2", LocalDate.of(2026, 3, 10), "food", 15_000),
            expense("e3", LocalDate.of(2026, 3, 15), "transport", 5_000),
            expense("e4", LocalDate.of(2026, 2, 28), "food", 99_999), // вне периода
        )
        val report = categoryReport(txns, rub, march, ReportKind.EXPENSE)
        val byCategory = report.associateBy { it.categoryId }
        assertEquals(Money(Minor(40_000), rub), byCategory.getValue("food").total)
        assertEquals(Money(Minor(5_000), rub), byCategory.getValue("transport").total)
        assertEquals(2, report.size)
    }

    // I-11: перевод не даёт строки в отчёте по категориям вообще.
    @Test
    fun `transfers never appear in a category report`() {
        val transfer = Txn.Transfer(
            "t1", LocalDate.of(2026, 3, 5), 0, 0,
            "cash", Money(Minor(10_000), rub), "savings", Money(Minor(10_000), rub),
        )
        val report = categoryReport(listOf(transfer), rub, march, ReportKind.EXPENSE)
        assertEquals(emptyList<CategoryTotal>(), report)
    }

    // ADR-012: отчёт всегда внутри одной валюты, операции в другой валюте не подмешиваются.
    @Test
    fun `does not mix currencies into one category total`() {
        val txns = listOf(
            expense("e1", LocalDate.of(2026, 3, 5), "food", 100_00, rub),
            expense("e2", LocalDate.of(2026, 3, 6), "food", 50_00, usd),
        )
        val rubReport = categoryReport(txns, rub, march, ReportKind.EXPENSE)
        assertEquals(Money(Minor(100_00), rub), rubReport.single().total)

        val usdReport = categoryReport(txns, usd, march, ReportKind.EXPENSE)
        assertEquals(Money(Minor(50_00), usd), usdReport.single().total)
    }

    @Test
    fun `income and expense reports do not leak into each other`() {
        val income = Txn.Income("i1", LocalDate.of(2026, 3, 1), 0, 0, "acc", Money(Minor(1000), rub), "salary")
        val expenseTxn = expense("e1", LocalDate.of(2026, 3, 2), "food", 500)
        val expenseReport = categoryReport(listOf(income, expenseTxn), rub, march, ReportKind.EXPENSE)
        assertEquals(1, expenseReport.size)
        assertEquals("food", expenseReport.single().categoryId)
    }
}
