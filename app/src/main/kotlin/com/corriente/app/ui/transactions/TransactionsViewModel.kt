package com.corriente.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.model.Account
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

data class TxnFilter(val accountId: String? = null, val currencyCode: String? = null)

data class TxnRow(
    val id: String,
    val title: String,
    val note: String?,
    val amountText: String,
    val editable: Boolean,
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
    val currencyCodes: List<String> = emptyList(),
    val filter: TxnFilter = TxnFilter(),
    val empty: Boolean = true,
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
): List<DaySection> {
    val filtered = txns.asSequence()
        .filter { filter.accountId == null || matchesAccount(it, filter.accountId) }
        .filter { filter.currencyCode == null || matchesCurrency(it, filter.currencyCode) }
        .toList()

    return filtered.groupBy { it.date }.entries
        .sortedByDescending { it.key }
        .map { (date, dayTxns) ->
            DaySection(
                date = date,
                totals = dayTotals(dayTxns, currenciesByCode),
                rows = dayTxns.map { txn -> row(txn, accountNames, categoryNames, currenciesByCode) },
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
): TxnRow = when (txn) {
    is Txn.Expense -> {
        val currency = currencyOrFallback(txn.amount.currency, byCode)
        TxnRow(
            id = txn.id,
            title = txn.categoryId?.let { categoryNames[it] } ?: "—",
            note = txn.note,
            amountText = MoneyFormatter.format(-txn.amount, currency),
            editable = true,
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
        )
        TransactionsUiState(
            sections = buildDaySections(
                txns = allTxns,
                filter = sanitizedFilter,
                accountNames = allAccounts.associate { it.id to it.name },
                categoryNames = allCategories.associate { it.id to it.name },
                currenciesByCode = byCode,
            ),
            accounts = allAccounts.filterNot { it.isArchived },
            currencyCodes = allTxns.flatMap { currenciesOf(it) }.distinct().sorted(),
            filter = sanitizedFilter,
            empty = allTxns.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setAccountFilter(accountId: String?) = filter.update { it.copy(accountId = accountId) }

    fun setCurrencyFilter(code: String?) = filter.update { it.copy(currencyCode = code) }

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
