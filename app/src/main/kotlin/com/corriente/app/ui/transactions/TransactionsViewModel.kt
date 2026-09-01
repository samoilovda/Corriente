package com.corriente.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Фильтры списка операций (T5.2). [query] ищет по заметке и названию категории без учёта
 * регистра. [minAmountMinor]/[maxAmountMinor] сравнивают модуль суммы в минорных единицах
 * (это фильтр, не арифметика — сложения разных валют тут нет, I-8 не нарушается).
 */
data class TxnFilter(
    val accountId: String? = null,
    val currencyCode: String? = null,
    val query: String = "",
    val categoryId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
) {
    val isActive: Boolean
        get() = accountId != null || currencyCode != null || query.isNotBlank() || categoryId != null ||
            from != null || to != null || minAmountMinor != null || maxAmountMinor != null
}

data class TxnRow(
    val id: String,
    val title: String,
    val note: String?,
    val amountText: String,
    /** true — расход/доход, открывается в экране ввода. */
    val editable: Boolean,
    /** true — перевод, открывается в экране перевода. */
    val isTransfer: Boolean = false,
    /** Эмодзи-иконка категории (T5.5), null у переводов и операций без категории. */
    val icon: String? = null,
    /** Цвет категории (ARGB); 0 — нет цвета. */
    val color: Int = 0,
)

/** Итог дня считается по каждой валюте отдельно (I-8) — сложение разных валют невозможно. */
data class DaySection(
    val date: LocalDate,
    val totals: List<String>,
    val rows: List<TxnRow>,
)

data class TransactionsUiState(
    val sections: List<DaySection> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencyCodes: List<String> = emptyList(),
    val filter: TxnFilter = TxnFilter(),
    val empty: Boolean = true,
    /** true — есть операции, но под активный фильтр ничего не подошло. */
    val noMatch: Boolean = false,
)

private fun matchesAccount(txn: Txn, accountId: String): Boolean = when (txn) {
    is Txn.Expense -> txn.accountId == accountId
    is Txn.Income -> txn.accountId == accountId
    is Txn.Transfer -> txn.fromAccountId == accountId || txn.toAccountId == accountId
}

private fun currenciesOf(txn: Txn): List<String> = when (txn) {
    is Txn.Expense -> listOf(txn.amount.currency.code)
    is Txn.Income -> listOf(txn.amount.currency.code)
    is Txn.Transfer -> listOf(txn.fromAmount.currency.code, txn.toAmount.currency.code)
}

private fun matchesCurrency(txn: Txn, code: String): Boolean = code in currenciesOf(txn)

private fun categoryIdOf(txn: Txn): String? = when (txn) {
    is Txn.Expense -> txn.categoryId
    is Txn.Income -> txn.categoryId
    is Txn.Transfer -> null
}

private fun amountMagnitudesOf(txn: Txn): List<Long> = when (txn) {
    is Txn.Expense -> listOf(txn.amount.amount.raw)
    is Txn.Income -> listOf(txn.amount.amount.raw)
    is Txn.Transfer -> listOf(txn.fromAmount.amount.raw, txn.toAmount.amount.raw)
}

private fun matchesQuery(txn: Txn, query: String, categoryNames: Map<String, String>): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    val haystack = buildList {
        txn.note?.let { add(it) }
        categoryIdOf(txn)?.let { categoryNames[it] }?.let { add(it) }
    }
    return haystack.any { it.lowercase().contains(needle) }
}

private fun matchesFilter(txn: Txn, filter: TxnFilter, categoryNames: Map<String, String>): Boolean {
    if (filter.accountId != null && !matchesAccount(txn, filter.accountId)) return false
    if (filter.currencyCode != null && !matchesCurrency(txn, filter.currencyCode)) return false
    if (filter.categoryId != null && categoryIdOf(txn) != filter.categoryId) return false
    if (filter.from != null && txn.date < filter.from) return false
    if (filter.to != null && txn.date > filter.to) return false
    if (!matchesQuery(txn, filter.query, categoryNames)) return false
    val magnitudes = amountMagnitudesOf(txn)
    if (filter.minAmountMinor != null && magnitudes.none { it >= filter.minAmountMinor }) return false
    if (filter.maxAmountMinor != null && magnitudes.none { it <= filter.maxAmountMinor }) return false
    return true
}

private fun currencyOrFallback(code: CurrencyCode, byCode: Map<String, Currency>): Currency =
    byCode[code.code] ?: Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

/**
 * Плоский список операций → секции по дням (свежие сверху), с итогом по каждой валюте.
 * Переводы показываются строкой, но в итог дня не входят (I-11).
 */
internal fun buildDaySections(
    txns: List<Txn>,
    filter: TxnFilter,
    accountNames: Map<String, String>,
    categoryNames: Map<String, String>,
    currenciesByCode: Map<String, Currency>,
    categoryIcons: Map<String, String?> = emptyMap(),
    categoryColors: Map<String, Int> = emptyMap(),
): List<DaySection> {
    val filtered = txns.filter { matchesFilter(it, filter, categoryNames) }

    return filtered.groupBy { it.date }.entries
        .sortedByDescending { it.key }
        .map { (date, dayTxns) ->
            DaySection(
                date = date,
                totals = dayTotals(dayTxns, currenciesByCode),
                rows = dayTxns.map { txn ->
                    row(txn, accountNames, categoryNames, currenciesByCode, categoryIcons, categoryColors)
                },
            )
        }
}

private fun dayTotals(dayTxns: List<Txn>, byCode: Map<String, Currency>): List<String> {
    val nets = mutableMapOf<String, Long>()
    dayTxns.forEach { txn ->
        when (txn) {
            is Txn.Expense -> nets.merge(txn.amount.currency.code, -txn.amount.amount.raw, Long::plus)
            is Txn.Income -> nets.merge(txn.amount.currency.code, txn.amount.amount.raw, Long::plus)
            is Txn.Transfer -> Unit
        }
    }
    return nets.entries.sortedBy { it.key }.map { (code, raw) ->
        val currency = currencyOrFallback(CurrencyCode(code), byCode)
        MoneyFormatter.format(Money(Minor(raw), currency.code), currency)
    }
}

private fun row(
    txn: Txn,
    accountNames: Map<String, String>,
    categoryNames: Map<String, String>,
    byCode: Map<String, Currency>,
    categoryIcons: Map<String, String?>,
    categoryColors: Map<String, Int>,
): TxnRow = when (txn) {
    is Txn.Expense -> {
        val currency = currencyOrFallback(txn.amount.currency, byCode)
        TxnRow(
            id = txn.id,
            title = txn.categoryId?.let { categoryNames[it] } ?: "—",
            note = txn.note,
            amountText = MoneyFormatter.format(-txn.amount, currency),
            editable = true,
            icon = txn.categoryId?.let { categoryIcons[it] },
            color = txn.categoryId?.let { categoryColors[it] } ?: 0,
        )
    }
    is Txn.Income -> {
        val currency = currencyOrFallback(txn.amount.currency, byCode)
        TxnRow(
            id = txn.id,
            title = txn.categoryId?.let { categoryNames[it] } ?: "—",
            note = txn.note,
            amountText = "+" + MoneyFormatter.format(txn.amount, currency),
            editable = true,
            icon = txn.categoryId?.let { categoryIcons[it] },
            color = txn.categoryId?.let { categoryColors[it] } ?: 0,
        )
    }
    is Txn.Transfer -> {
        val from = currencyOrFallback(txn.fromAmount.currency, byCode)
        val to = currencyOrFallback(txn.toAmount.currency, byCode)
        TxnRow(
            id = txn.id,
            title = "${accountNames[txn.fromAccountId] ?: "?"} → ${accountNames[txn.toAccountId] ?: "?"}",
            note = txn.note,
            amountText = "${MoneyFormatter.format(txn.fromAmount, from)} → ${MoneyFormatter.format(txn.toAmount, to)}",
            editable = false,
            isTransfer = true,
        )
    }
}

class TransactionsViewModel(
    txns: TxnRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    currencies: CurrencyRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TxnFilter())

    val uiState: StateFlow<TransactionsUiState> = combine(
        txns.observeAll(),
        accounts.observeAll(),
        categories.observeAllForLookup(),
        currencies.observeAll(),
        filter,
    ) { allTxns, allAccounts, allCategories, allCurrencies, currentFilter ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val sanitizedFilter = currentFilter.copy(
            accountId = currentFilter.accountId?.takeIf { id -> allAccounts.any { it.id == id } },
            currencyCode = currentFilter.currencyCode?.takeIf { code -> byCode.containsKey(code) },
            categoryId = currentFilter.categoryId?.takeIf { id -> allCategories.any { it.id == id } },
        )
        val sections = buildDaySections(
            txns = allTxns,
            filter = sanitizedFilter,
            accountNames = allAccounts.associate { it.id to it.name },
            categoryNames = allCategories.associate { it.id to it.name },
            currenciesByCode = byCode,
            categoryIcons = allCategories.associate { it.id to it.icon },
            categoryColors = allCategories.associate { it.id to it.color },
        )
        TransactionsUiState(
            sections = sections,
            accounts = allAccounts.filterNot { it.isArchived },
            categories = allCategories.filterNot { it.isArchived },
            currencyCodes = allTxns.flatMap { currenciesOf(it) }.distinct().sorted(),
            filter = sanitizedFilter,
            empty = allTxns.isEmpty(),
            noMatch = allTxns.isNotEmpty() && sections.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setAccountFilter(accountId: String?) = filter.update { it.copy(accountId = accountId) }

    fun setCurrencyFilter(code: String?) = filter.update { it.copy(currencyCode = code) }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setCategoryFilter(categoryId: String?) = filter.update { it.copy(categoryId = categoryId) }

    fun setPeriod(from: LocalDate?, to: LocalDate?) = filter.update { it.copy(from = from, to = to) }

    fun setAmountRange(minMinor: Long?, maxMinor: Long?) =
        filter.update { it.copy(minAmountMinor = minMinor, maxAmountMinor = maxMinor) }

    fun clearFilters() = filter.update { TxnFilter() }

    companion object {
        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
        ) = viewModelFactory {
            initializer { TransactionsViewModel(txns, accounts, categories, currencies) }
        }
    }
}
