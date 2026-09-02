package com.corriente.app.ui.widgetsettings

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.widget.MAX_PINNED_CURRENCIES
import com.corriente.data.widget.MAX_QUICK_CATEGORIES
import com.corriente.data.widget.WidgetConfig
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.data.widget.defaultPinnedCurrencies
import com.corriente.data.widget.defaultQuickCategories
import com.corriente.money.Currency
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CurrencyRow(val code: String, val symbol: String, val pinned: Boolean)
data class AccountRow(val id: String, val name: String, val currency: String, val active: Boolean)
data class CategoryRow(val id: String, val name: String, val icon: String?, val pinned: Boolean)

data class WidgetSettingsUiState(
    val currencies: List<CurrencyRow> = emptyList(),
    val accounts: List<AccountRow> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
) {
    val pinnedCount: Int get() = currencies.count { it.pinned }
    val canPinMore: Boolean get() = pinnedCount < MAX_PINNED_CURRENCIES

    val pinnedCategoryCount: Int get() = categories.count { it.pinned }
    val canPinMoreCategories: Boolean get() = pinnedCategoryCount < MAX_QUICK_CATEGORIES
}

/** Чистая свёртка состояния экрана — тестируется без Android/DataStore. */
internal fun widgetSettingsUiState(
    currencies: List<Currency>,
    accounts: List<Account>,
    categories: List<Category>,
    transactions: List<Txn>,
    config: WidgetConfig,
    today: LocalDate,
): WidgetSettingsUiState {
    val effectivePinned = config.pinnedCurrencyCodes.ifEmpty {
        defaultPinnedCurrencies(accounts, transactions, today).map { it.code }
    }.toSet()
    val effectiveActive = config.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.firstOrNull()?.id
    // R4.3: те же категории, что покажет виджет — закреплённые вручную, иначе автоподбор.
    val effectivePinnedCategories = config.pinnedCategoryIds.ifEmpty {
        defaultQuickCategories(transactions, categories, today).map { it.id }
    }.toSet()
    return WidgetSettingsUiState(
        currencies = currencies.map { CurrencyRow(it.code.code, it.symbol, it.code.code in effectivePinned) },
        accounts = accounts.map { AccountRow(it.id, it.name, it.currency.code, it.id == effectiveActive) },
        categories = categories.map { CategoryRow(it.id, it.name, it.icon, it.id in effectivePinnedCategories) },
    )
}

/** Новый список закреплённых валют после тапа по [code] (кап [MAX_PINNED_CURRENCIES]). */
internal fun nextPinnedCurrencies(current: List<String>, code: String): List<String> = when {
    code in current -> current - code
    current.size < MAX_PINNED_CURRENCIES -> current + code
    else -> current
}

/** R4.3: то же самое для категорий, кап [MAX_QUICK_CATEGORIES]. */
internal fun nextPinnedCategories(current: List<String>, categoryId: String): List<String> = when {
    categoryId in current -> current - categoryId
    current.size < MAX_QUICK_CATEGORIES -> current + categoryId
    else -> current
}

/**
 * T4.4/R4.3: состояние экрана настроек виджета. Разрешение выбора «эффективных»
 * валют/счёта/категорий.
 */
class WidgetSettingsViewModel(
    private val accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    txnRepository: TxnRepository,
    categoryRepository: CategoryRepository,
    private val configStore: WidgetConfigStore,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    val uiState: StateFlow<WidgetSettingsUiState> = combine(
        currencyRepository.observeActive(),
        accountRepository.observeActive(),
        categoryRepository.observeActive(),
        txnRepository.observeAll(),
        configStore.config,
    ) { currencies, accounts, categories, txns, config ->
        widgetSettingsUiState(currencies, accounts, categories, txns, config, today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetSettingsUiState())

    fun toggleCurrency(code: String) {
        launchWrite(onError = { "Не удалось сохранить настройки виджета" }) {
            val current = uiState.value.currencies.filter { it.pinned }.map { it.code }
            configStore.setPinnedCurrencies(nextPinnedCurrencies(current, code))
        }
    }

    /** R4.3: закрепление категории — до [com.corriente.data.widget.MAX_QUICK_CATEGORIES]. */
    fun toggleCategory(categoryId: String) {
        launchWrite(onError = { "Не удалось сохранить настройки виджета" }) {
            val current = uiState.value.categories.filter { it.pinned }.map { it.id }
            configStore.setPinnedCategories(nextPinnedCategories(current, categoryId))
        }
    }

    fun setActiveAccount(id: String) {
        launchWrite(onError = { "Не удалось сохранить настройки виджета" }) { configStore.setActiveAccount(id) }
    }

    fun resetToDefaults() {
        launchWrite(onError = { "Не удалось сбросить настройки виджета" }) { configStore.reset() }
    }

    companion object {
        fun factory(
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            txnRepository: TxnRepository,
            categoryRepository: CategoryRepository,
            configStore: WidgetConfigStore,
        ) = viewModelFactory {
            initializer {
                WidgetSettingsViewModel(
                    accountRepository, currencyRepository, txnRepository, categoryRepository, configStore,
                )
            }
        }
    }
}
