package com.corriente.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Account
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.usecase.AccountBalanceUseCase
import com.corriente.data.usecase.totalsByCurrency
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Счёт вместе с его валютой — [Currency] нужна для форматирования сумм (MoneyFormatter). */
data class AccountRow(val account: Account, val currency: Currency, val balance: Money? = null)

/**
 * Счета одной валюты и итог по ней (T1.7). [total] = null, если ни один счёт валюты не помечен
 * `include_in_total`. Разные валюты — разные группы; складывать их между собой невозможно (I-8).
 */
data class CurrencyBalanceGroup(
    val currency: Currency,
    val rows: List<AccountRow>,
    val total: Money?,
)

data class AccountsUiState(
    val groups: List<CurrencyBalanceGroup> = emptyList(),
    val archived: List<AccountRow> = emptyList(),
    val activeCurrencies: List<Currency> = emptyList(),
)

/** Форма создания/редактирования счёта: значения ровно как их ввёл пользователь. */
data class AccountForm(
    val name: String,
    val currency: CurrencyCode,
    val kind: AccountKind,
    val openingBalanceText: String,
    val includeInTotal: Boolean,
)

/**
 * Открытый редактор счёта. [editingId] = null — создание нового. [currencyLocked] = у счёта
 * уже есть операции: валюта и начальный остаток стали историческими фактами, поля недоступны (I-23).
 */
data class AccountEditor(
    val editingId: String?,
    val currencyLocked: Boolean,
    val name: String,
    val currency: CurrencyCode?,
    val kind: AccountKind,
    val openingBalanceText: String,
    val includeInTotal: Boolean,
)

private fun fallbackCurrency(code: CurrencyCode): Currency =
    Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

private fun Account.toRow(currenciesByCode: Map<String, Currency>): AccountRow =
    AccountRow(this, currenciesByCode[currency.code] ?: fallbackCurrency(currency))

/**
 * Разбор поля «начальный остаток». Величина — через инвариант-безопасный [AmountInput] (I-25),
 * знак — отдельно: счёт-долг стартует с отрицательного остатка, а [AmountInput] по контракту
 * всегда неотрицателен.
 */
internal fun openingBalanceMinor(text: String, currency: Currency): Minor {
    val negative = text.trimStart().startsWith("-")
    val magnitude = AmountInput.fromText(text, currency).toMinorOrNull(currency)?.raw ?: 0L
    return Minor(if (negative) -magnitude else magnitude)
}

/**
 * Минорные единицы → строка для поля ввода ("1234.56", без группировки и символа), такая, что
 * [openingBalanceMinor] разбирает её обратно в то же число. Отрицательный остаток сохраняет знак.
 */
internal fun openingBalanceText(minor: Minor, currency: Currency): String {
    if (minor.raw == 0L) return ""
    val negative = minor.raw < 0
    val digits = (if (negative) -minor.raw else minor.raw).toString().padStart(currency.minorUnits + 1, '0')
    val body = if (currency.minorUnits == 0) {
        digits
    } else {
        "${digits.dropLast(currency.minorUnits)}.${digits.takeLast(currency.minorUnits)}"
    }
    return if (negative) "-$body" else body
}

class AccountsViewModel(
    private val accounts: AccountRepository,
    private val currencies: CurrencyRepository,
    balances: AccountBalanceUseCase,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        balances.observeBalances(),
        accounts.observeArchived(),
        currencies.observeAll(),
        currencies.observeActive(),
    ) { accountBalances, archived, allCurrencies, activeCurrencies ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val totals = totalsByCurrency(accountBalances)
        val groups = accountBalances.groupBy { it.account.currency }.entries
            .sortedBy { it.key.code }
            .map { (code, group) ->
                val currency = byCode[code.code] ?: fallbackCurrency(code)
                CurrencyBalanceGroup(
                    currency = currency,
                    rows = group.sortedBy { it.account.displayOrder }
                        .map { AccountRow(it.account, currency, it.balance) },
                    total = totals[code],
                )
            }
        AccountsUiState(
            groups = groups,
            archived = archived.map { it.toRow(byCode) },
            activeCurrencies = activeCurrencies,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    private val _editor = MutableStateFlow<AccountEditor?>(null)
    val editor: StateFlow<AccountEditor?> = _editor

    fun startCreate() {
        _editor.value = AccountEditor(
            editingId = null,
            currencyLocked = false,
            name = "",
            currency = uiState.value.activeCurrencies.firstOrNull()?.code,
            kind = AccountKind.CASH,
            openingBalanceText = "",
            includeInTotal = true,
        )
    }

    fun startEdit(account: Account) {
        viewModelScope.launch {
            val currency = currencies.getByCode(account.currency) ?: fallbackCurrency(account.currency)
            _editor.value = AccountEditor(
                editingId = account.id,
                currencyLocked = accounts.hasTransactions(account.id),
                name = account.name,
                currency = account.currency,
                kind = account.kind,
                openingBalanceText = openingBalanceText(account.openingBalance.amount, currency),
                includeInTotal = account.includeInTotal,
            )
        }
    }

    fun closeEditor() {
        _editor.value = null
    }

    /** @return false, если форма невалидна (пустое имя или не выбрана валюта) — редактор остаётся открыт. */
    fun save(form: AccountForm): Boolean {
        if (form.name.isBlank()) return false
        val editor = _editor.value

        viewModelScope.launch {
            val currency = currencies.getByCode(form.currency) ?: fallbackCurrency(form.currency)
            val money = Money(openingBalanceMinor(form.openingBalanceText, currency), form.currency)
            val id = editor?.editingId
            if (id == null) {
                accounts.create(
                    name = form.name.trim(),
                    currency = form.currency,
                    kind = form.kind,
                    openingBalance = money,
                    color = 0,
                    includeInTotal = form.includeInTotal,
                )
            } else {
                accounts.rename(id, form.name.trim(), color = 0, icon = null, includeInTotal = form.includeInTotal)
                if (editor.currencyLocked.not()) {
                    accounts.setCurrencyAndOpeningBalanceBeforeFirstUse(id, form.currency, money)
                }
            }
            _editor.value = null
        }
        return true
    }

    fun archive(id: String) {
        viewModelScope.launch { accounts.archive(id) }
    }

    fun unarchive(id: String) {
        viewModelScope.launch { accounts.unarchive(id) }
    }

    fun deleteIfUnused(id: String) {
        viewModelScope.launch { accounts.deleteIfUnused(id) }
    }

    companion object {
        fun factory(
            accounts: AccountRepository,
            currencies: CurrencyRepository,
            balances: AccountBalanceUseCase,
        ) = viewModelFactory {
            initializer { AccountsViewModel(accounts, currencies, balances) }
        }
    }
}
