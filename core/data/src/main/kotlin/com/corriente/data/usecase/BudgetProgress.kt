package com.corriente.data.usecase

import com.corriente.data.model.Budget
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money

/**
 * «Потрачено из бюджета» на экране отчёта (R2.3).
 *
 * [percent] — целые проценты потраченного (`spent * 100 / budget.amount`), может быть больше 100
 * (перерасход); `null` при нулевом бюджете, где доля лишена смысла (0/0 — не 0% и не 100%, экран
 * обязан показать это иначе, не процентом). I-1: деньги/производные от них — никогда
 * `Double`/`Float`, даже проценты; `Float` появляется только на границе с Compose
 * (`LinearProgressIndicator`), в самом экране, не здесь.
 *
 * [isOverBudget] — отдельный флаг, а не `percent > 100`, чтобы вызывающий код не полагался на
 * сравнение процента (который может быть `null`) там, где речь идёт о факте перерасхода.
 */
data class BudgetProgress(
    val budget: Budget,
    val spent: Money,
    val remaining: Money,
    val percent: Int?,
) {
    val isOverBudget: Boolean get() = remaining.isNegative
}

/**
 * Чистая функция над уже посчитанным [categoryReport] (T1.8/R2.3) — здесь нет обращений к БД.
 *
 * Бюджет категории (`categoryId != null`) сравнивается с суммой её категории; бюджет «на всё»
 * (`categoryId == null`) — с суммой всех категорий отчёта. Оба относятся ровно к одной [currency]
 * (ADR-012/I-8): [report] обязан быть уже посчитан в этой валюте (см. [categoryReport]), а
 * бюджеты других валют из [budgets] просто пропускаются — это не ошибка вызывающего кода, а
 * нормальный сценарий «на экране выбрана RUB, бюджет — в USD, покажется на другой вкладке».
 */
fun budgetProgress(
    budgets: List<Budget>,
    report: List<CategoryTotal>,
    currency: CurrencyCode,
): List<BudgetProgress> {
    val zero = Money(Minor(0), currency)
    val spentByCategory: Map<String?, Money> = report
        .filter { it.total.currency == currency }
        .associate { it.categoryId to it.total }
    val totalSpent: Money = spentByCategory.values.fold(zero, Money::plus)

    return budgets
        .filter { it.amount.currency == currency }
        .map { budget ->
            val spent = if (budget.categoryId == null) totalSpent else spentByCategory[budget.categoryId] ?: zero
            val remaining = budget.amount - spent
            // I-3: умножение через Math.multiplyExact — переполнение обязано падать, не молчать.
            val percent = if (budget.amount.amount.raw == 0L) {
                null
            } else {
                (Math.multiplyExact(spent.amount.raw, 100L) / budget.amount.amount.raw).toInt()
            }
            BudgetProgress(budget, spent, remaining, percent)
        }
}
