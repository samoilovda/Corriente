package com.corriente.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.category.OverviewCategoriesSettings
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.AccountBalance
import com.corriente.data.usecase.AccountBalanceUseCase
import com.corriente.data.usecase.totalsByCurrency
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import com.corriente.data.usecase.MonthOverview
import com.corriente.data.usecase.buildMonthOverview

/** Состояние шапки; [overview] == null — нет счетов (шапку не показываем). */
data class MonthOverviewUiState(val overview: MonthOverview? = null)

/**
 * Данные шапки «Операций». Выбор счёта общий со списком (его задаёт экран через [setAccount] и фильтр
 * списка); валюта для «Все счета» и месяц — только для шапки, список они не фильтруют.
 */
class MonthOverviewViewModel(
    txns: TxnRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    currencies: CurrencyRepository,
    balances: AccountBalanceUseCase,
    overviewSettings: OverviewCategoriesSettings? = null,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val accountId = MutableStateFlow<String?>(null)
    private val currency = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTxns = month.flatMapLatest { m -> txns.observeRange(m.atDay(1), m.atEndOfMonth()) }

    val uiState: StateFlow<MonthOverviewUiState> = combine(
        combine(month, monthTxns) { m, list -> m to list },
        accounts.observeAll(),
        categories.observeAllForLookup(),
        combine(currencies.observeAll(), balances.observeBalances()) { c, b -> c to b },
        combine(accountId, currency, overviewSettings?.pinned ?: flowOf(emptyList())) { a, c, p -> Triple(a, c, p) },
    ) { (m, list), allAccounts, allCategories, (allCurrencies, allBalances), (selAccount, selCurrency, pins) ->
        MonthOverviewUiState(
            buildMonthOverview(m, list, allAccounts, allCategories, allCurrencies, allBalances, selAccount, selCurrency, pins),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthOverviewUiState())

    fun shiftMonth(delta: Long) = month.update { it.plusMonths(delta) }

    fun setAccount(id: String?) {
        accountId.value = id
    }

    /** «Все счета» в валюте [code]. */
    fun setAllAccounts(code: String) {
        accountId.value = null
        currency.value = code
    }

    companion object {
        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            balances: AccountBalanceUseCase,
            overviewSettings: OverviewCategoriesSettings? = null,
        ) = viewModelFactory {
            initializer { MonthOverviewViewModel(txns, accounts, categories, currencies, balances, overviewSettings) }
        }
    }
}
