package com.corriente.app.ui.accountbalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.report.PeriodMode
import com.corriente.app.ui.report.periodLabel
import com.corriente.app.ui.report.periodRange
import com.corriente.app.ui.report.shiftAnchor
import com.corriente.data.model.Account
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.balanceSeries
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** R3.2: одна точка графика — остаток на конец дня, уже посчитанный (I-1: Float только в Canvas). */
data class BalancePointUi(val date: LocalDate, val valueMinor: Long, val amountText: String)

data class AccountBalanceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val periodMode: PeriodMode = PeriodMode.MONTH,
    val periodLabel: String = "",
    val points: List<BalancePointUi> = emptyList(),
    val currentBalanceText: String? = null,
)

/**
 * R3.2: «динамика по счёту» — остаток выбранного счёта день за днём за период, накопительным
 * итогом поверх [balanceSeries] (T1.7/[com.corriente.data.usecase.accountBalance]). Одна
 * валюта — валюта счёта (I-8, ADR-012): здесь конвертировать нечего и незачем, счёт всегда
 * в одной и той же валюте (I-23).
 */
class AccountBalanceViewModel(
    private val accounts: AccountRepository,
    private val txns: TxnRepository,
    private val currencies: CurrencyRepository,
    initialAccountId: String? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private data class Form(
        val accountId: String?,
        val mode: PeriodMode = PeriodMode.MONTH,
        val anchor: LocalDate,
        val customStart: LocalDate,
        val customEnd: LocalDate,
    )

    private val form = MutableStateFlow(
        today().let { Form(accountId = initialAccountId, anchor = it, customStart = it.withDayOfMonth(1), customEnd = it) },
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AccountBalanceUiState> = form.flatMapLatest { f ->
        combine(accounts.observeAll(), txns.observeAll(), currencies.observeAll()) { allAccounts, allTxns, allCurrencies ->
            val active = allAccounts.filterNot { it.isArchived }
            val account = active.firstOrNull { it.id == f.accountId } ?: active.firstOrNull()
            val range = periodRange(f.mode, f.anchor, f.customStart, f.customEnd)

            if (account == null) {
                AccountBalanceUiState(accounts = active, periodMode = f.mode, periodLabel = periodLabel(f.mode, range))
            } else {
                val byCode = allCurrencies.associateBy { it.code.code }
                val currency = byCode[account.currency.code] ?: fallbackCurrency(account.currency)
                val series = balanceSeries(account, allTxns, range)
                AccountBalanceUiState(
                    accounts = active,
                    selectedAccountId = account.id,
                    periodMode = f.mode,
                    periodLabel = periodLabel(f.mode, range),
                    points = series.map { point ->
                        BalancePointUi(
                            date = point.date,
                            valueMinor = point.balance.amount.raw,
                            amountText = MoneyFormatter.format(point.balance, currency),
                        )
                    },
                    currentBalanceText = series.lastOrNull()?.let { MoneyFormatter.format(it.balance, currency) },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountBalanceUiState())

    fun selectAccount(accountId: String) = form.update { it.copy(accountId = accountId) }

    fun setPeriodMode(mode: PeriodMode) = form.update { it.copy(mode = mode) }

    fun shiftPeriod(delta: Long) = form.update { it.copy(anchor = shiftAnchor(it.mode, it.anchor, delta)) }

    fun setCustomRange(start: LocalDate, end: LocalDate) =
        form.update { it.copy(mode = PeriodMode.CUSTOM, customStart = start, customEnd = end) }

    companion object {
        fun factory(
            accounts: AccountRepository,
            txns: TxnRepository,
            currencies: CurrencyRepository,
            initialAccountId: String? = null,
        ) = viewModelFactory {
            initializer { AccountBalanceViewModel(accounts, txns, currencies, initialAccountId) }
        }
    }
}

private fun fallbackCurrency(code: CurrencyCode): Currency = Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)
