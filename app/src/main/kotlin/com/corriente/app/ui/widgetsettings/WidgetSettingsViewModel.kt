package com.corriente.app.ui.widgetsettings

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.data.model.Account
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.widget.MAX_PINNED_CURRENCIES
import com.corriente.data.widget.WidgetConfig
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.data.widget.defaultPinnedCurrencies
import com.corriente.money.Currency
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CurrencyRow(val code: String, val symbol: String, val pinned: Boolean)
data class AccountRow(val id: String, val name: String, val currency: String, val active: Boolean)

data class WidgetSettingsUiState(
    val currencies: List<CurrencyRow> = emptyList(),
    val accounts: List<AccountRow> = emptyList(),
) {
    val pinnedCount: Int get() = currencies.count { it.pinned }
    val canPinMore: Boolean get() = pinnedCount < MAX_PINNED_CURRENCIES
}

/** Чистая свёртка состояния экрана — тестируется без Android/DataStore. */
internal fun widgetSettingsUiState(
    currencies: List<Currency>,
    accounts: List<Account>,
    transactions: List<Txn>,
    config: WidgetConfig,
    today: LocalDate,
): WidgetSettingsUiState {
    val effectivePinned = config.pinnedCurrencyCodes.ifEmpty {
        defaultPinnedCurrencies(accounts, transactions, today).map { it.code }
    }.toSet()
    val effectiveActive = config.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.firstOrNull()?.id
    return WidgetSettingsUiState(
        currencies = currencies.map { CurrencyRow(it.code.code, it.symbol, it.code.code in effectivePinned) },
        accounts = accounts.map { AccountRow(it.id, it.name, it.currency.code, it.id == effectiveActive) },
    )
}

/** Новый список закреплённых валют после тапа по [code] (кап [MAX_PINNED_CURRENCIES]). */
internal fun nextPinnedCurrencies(current: List<String>, code: String): List<String> = when {
    code in current -> current - code
    current.size < MAX_PINNED_CURRENCIES -> current + code
    else -> current
}

/** T4.4: состояние экрана настроек виджета. Разрешение выбора «эффективных» валют/счёта. */
class WidgetSettingsViewModel(
    private val accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    txnRepository: TxnRepository,
    private val configStore: WidgetConfigStore,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    val uiState: StateFlow<WidgetSettingsUiState> = combine(
        currencyRepository.observeActive(),
        accountRepository.observeActive(),
        txnRepository.observeAll(),
        configStore.config,
    ) { currencies, accounts, txns, config ->
        widgetSettingsUiState(currencies, accounts, txns, config, today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetSettingsUiState())

    fun toggleCurrency(code: String) {
        launchWrite(onError = { "Не удалось сохранить настройки виджета" }) {
            val current = uiState.value.currencies.filter { it.pinned }.map { it.code }
            configStore.setPinnedCurrencies(nextPinnedCurrencies(current, code))
        }
    }

    fun setActiveAccount(id: String) {
        launchWrite(onError = { "Не удалось сохранить настройки виджета" }) { configStore.setActiveAccount(id) }
    }

    companion object {
        fun factory(
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            txnRepository: TxnRepository,
            configStore: WidgetConfigStore,
        ) = viewModelFactory {
            initializer {
                WidgetSettingsViewModel(accountRepository, currencyRepository, txnRepository, configStore)
            }
        }
    }
}
