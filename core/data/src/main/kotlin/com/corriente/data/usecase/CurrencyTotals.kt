package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.sumMoney
import java.time.LocalDate

/** Доходы и расходы за период в одной валюте; [incomeCount]/[expenseCount] — число операций. */
data class CurrencyTotals(
    val currency: CurrencyCode,
    val income: Money,
    val expense: Money,
    val incomeCount: Int,
    val expenseCount: Int,
)

/**
 * Отчёт «Все валюты»: все доходы и все расходы за период — по строке на валюту, без пересчёта (I-8: суммы разных
 * валют не складываются). Переводы и корректировки баланса не входят (I-11). Порядок — по числу операций (больше —
 * выше), при равенстве — по коду валюты.
 */
fun currencyTotals(transactions: List<Txn>, period: ClosedRange<LocalDate>): List<CurrencyTotals> {
    val incomes = mutableMapOf<CurrencyCode, MutableList<Money>>()
    val expenses = mutableMapOf<CurrencyCode, MutableList<Money>>()
    for (txn in transactions) {
        if (txn.date !in period) continue
        when (txn) {
            is Txn.Income -> incomes.getOrPut(txn.amount.currency) { mutableListOf() }.add(txn.amount)
            is Txn.Expense -> expenses.getOrPut(txn.amount.currency) { mutableListOf() }.add(txn.amount)
            is Txn.Transfer, is Txn.Adjustment -> Unit
        }
    }
    return (incomes.keys + expenses.keys)
        .map { code ->
            val inc = incomes[code].orEmpty()
            val exp = expenses[code].orEmpty()
            CurrencyTotals(
                currency = code,
                income = if (inc.isEmpty()) Money.zero(code) else inc.sumMoney(),
                expense = if (exp.isEmpty()) Money.zero(code) else exp.sumMoney(),
                incomeCount = inc.size,
                expenseCount = exp.size,
            )
        }
        .sortedWith(compareByDescending<CurrencyTotals> { it.incomeCount + it.expenseCount }.thenBy { it.currency.code })
}
