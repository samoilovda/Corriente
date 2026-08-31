package com.corriente.app.ui.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.model.ManagedCurrency
import com.corriente.data.repository.CurrencyRepository
import com.corriente.money.CurrencyCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CurrenciesUiState(
    val query: String = "",
    val currencies: List<ManagedCurrency> = emptyList(),
)

/**
 * Фильтрация справочника по коду или названию (T1.2). Регистр не важен, пустой запрос —
 * весь список. Разбор ввода тут не денежный (это строка поиска, не сумма), I-25 не затрагивает.
 */
internal fun filterCurrencies(all: List<ManagedCurrency>, query: String): List<ManagedCurrency> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return all
    return all.filter { currency ->
        currency.code.code.lowercase().contains(needle) || currency.name.lowercase().contains(needle)
    }
}

/** Допустимая отображаемая точность: не больше, чем минорных единиц по ISO (см. [ManagedCurrency]). */
internal fun isValidDisplayScale(minorUnits: Int, displayScale: Int): Boolean =
    displayScale in 0..minorUnits

/**
 * T1.2: экран выбора активных валют. Вся логика — здесь и в чистых функциях выше;
 * Composable-слой только рисует состояние и зовёт эти методы (BUILD_PLAN.md §0 правило 2).
 */
class CurrenciesViewModel(private val repository: CurrencyRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<CurrenciesUiState> =
        combine(repository.observeManaged(), query) { currencies, currentQuery ->
            CurrenciesUiState(
                query = currentQuery,
                currencies = filterCurrencies(currencies, currentQuery),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrenciesUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun setActive(code: CurrencyCode, active: Boolean) {
        viewModelScope.launch { repository.setActive(code, active) }
    }

    /**
     * Правка символа и отображаемой точности. Некорректную точность гасим здесь же —
     * [CurrencyRepository.updateDisplay] всё равно её отвергнет, но до него доводить незачем.
     */
    fun updateDisplay(code: CurrencyCode, symbol: String, displayScale: Int, minorUnits: Int) {
        if (!isValidDisplayScale(minorUnits, displayScale)) return
        viewModelScope.launch { repository.updateDisplay(code, symbol, displayScale) }
    }

    companion object {
        fun factory(repository: CurrencyRepository) = viewModelFactory {
            initializer { CurrenciesViewModel(repository) }
        }
    }
}
