package com.corriente.data.widget

import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.usecase.accountBalance
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import com.corriente.money.sumMoney
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/**
 * T4.1: снимок для виджета (ARCHITECTURE.md §4.2). Виджет живёт в чужом процессе и не читает
 * Room — он рисует готовые строки отсюда. Деньги в снимке — **строки** (I-1 буквально):
 * форматирование делается здесь, где есть `display_scale`, а не в композиции Glance.
 *
 * ADR-012: свести мультивалютный баланс в одно число нельзя — по строке на закреплённую валюту.
 */
@Serializable
data class WidgetSnapshot(
    val balances: List<CurrencyLine>,
    val monthExpenses: List<CurrencyLine>,
    val quickCategories: List<QuickCategory>,
    val activeAccountId: String,
    val computedAt: Long,
) {
    companion object {
        /** Заглушка до первого пересчёта / при недоступном хранилище (Direct Boot). */
        val EMPTY = WidgetSnapshot(emptyList(), emptyList(), emptyList(), "", 0L)
    }
}

@Serializable
data class CurrencyLine(val code: String, val formatted: String)

@Serializable
data class QuickCategory(val id: String, val name: String, val icon: String?, val color: Int)

/** Не более 6 — ограничение размера транзакции RemoteViews (ARCHITECTURE.md §4.4 п.5). */
const val MAX_QUICK_CATEGORIES = 6

/** Окно, по которому считаются «самые частые» категории для быстрого ввода. */
const val QUICK_CATEGORY_WINDOW_DAYS = 60L

/**
 * Чистая функция: те же входы → тот же снимок. Никаких обращений к БД, времени, локали.
 *
 * @param pinnedCurrencies закреплённые в настройках виджета валюты (1–3), в порядке показа.
 * @param today «сегодня» для окна частых категорий и определения текущего месяца.
 */
fun buildWidgetSnapshot(
    accounts: List<Account>,
    transactions: List<Txn>,
    currencies: List<Currency>,
    categories: List<Category>,
    pinnedCurrencies: List<CurrencyCode>,
    activeAccountId: String,
    today: LocalDate,
    computedAt: Long,
): WidgetSnapshot {
    val currencyByCode = currencies.associateBy { it.code }
    val activeAccounts = accounts.filterNot { it.isArchived }

    val balances = pinnedCurrencies.mapNotNull { code ->
        val meta = currencyByCode[code] ?: return@mapNotNull null
        val inTotal = activeAccounts.filter { it.currency == code && it.includeInTotal }
        if (inTotal.isEmpty()) return@mapNotNull null
        val sum = inTotal
            .map { accountBalance(it, transactions) }
            .reduce { a, b -> a + b }
        CurrencyLine(code.code, MoneyFormatter.format(sum, meta))
    }

    val month = YearMonth.from(today)
    val monthExpenseByCurrency: Map<CurrencyCode, Money> = transactions
        .filterIsInstance<Txn.Expense>()
        .filter { YearMonth.from(it.date) == month }
        .groupBy { it.amount.currency }
        .mapValues { (_, list) -> list.map { it.amount }.sumMoney() }

    val monthExpenses = pinnedCurrencies.mapNotNull { code ->
        val meta = currencyByCode[code] ?: return@mapNotNull null
        val spent = monthExpenseByCurrency[code] ?: Money(Minor(0), code)
        CurrencyLine(code.code, MoneyFormatter.format(spent, meta))
    }

    val windowStart = today.minusDays(QUICK_CATEGORY_WINDOW_DAYS)
    val categoryById = categories.associateBy { it.id }
    val frequency = LinkedHashMap<String, Int>()
    transactions
        .asSequence()
        .filter { it.date >= windowStart && it.date <= today }
        .forEach { txn ->
            val categoryId = when (txn) {
                is Txn.Expense -> txn.categoryId
                is Txn.Income -> txn.categoryId
                is Txn.Transfer -> null
            } ?: return@forEach
            val category = categoryById[categoryId] ?: return@forEach
            if (category.isArchived) return@forEach
            frequency[categoryId] = (frequency[categoryId] ?: 0) + 1
        }

    val quickCategories = frequency.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_QUICK_CATEGORIES)
        .mapNotNull { (id, _) ->
            val category = categoryById[id] ?: return@mapNotNull null
            QuickCategory(category.id, category.name, category.icon, category.color)
        }

    return WidgetSnapshot(
        balances = balances,
        monthExpenses = monthExpenses,
        quickCategories = quickCategories,
        activeAccountId = activeAccountId,
        computedAt = computedAt,
    )
}
