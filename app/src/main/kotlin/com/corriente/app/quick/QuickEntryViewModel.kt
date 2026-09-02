package com.corriente.app.quick

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.txnentry.EntryKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Счёт как вариант выбора в окне быстрого ввода — с уже разрешённой [Currency] для клавиатуры. */
data class QuickAccountOption(val id: String, val name: String, val currency: Currency)

data class QuickEntryUiState(
    val kind: EntryKind = EntryKind.EXPENSE,
    val categoryName: String = "",
    val accounts: List<QuickAccountOption> = emptyList(),
    val selectedAccountId: String? = null,
    val amount: AmountInput = AmountInput.empty(),
    /** F2.5-подобный флаг: false до первой эмиссии репозитория счетов. */
    val loaded: Boolean = false,
    val saving: Boolean = false,
) {
    val selectedAccount: QuickAccountOption? get() = accounts.firstOrNull { it.id == selectedAccountId }
    val currency: Currency? get() = selectedAccount?.currency

    val canSave: Boolean
        get() {
            val currency = currency ?: return false
            return (amount.toMinorOrNull(currency)?.raw ?: 0L) > 0L
        }
}

/**
 * R4.2: логика окна быстрого ввода из виджета — вынесена из `QuickExpenseActivity`, чтобы
 * покрыть юнит-тестами (переключатель расход/доход, выбор счёта, сохранение).
 *
 * Счёт, выбранный здесь ([selectAccount]), применяется только к этой операции — активный счёт
 * виджета ([com.corriente.data.widget.WidgetConfigStore]) не трогается; тот, что виджет считал
 * активным на момент тапа по категории, приходит сюда через [initialAccountId] как обычный
 * стартовый параметр, а не как постоянная подписка на конфиг.
 */
class QuickEntryViewModel(
    private val txnRepository: TxnRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    private val categoryId: String?,
    private val categoryName: String,
    initialAccountId: String?,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    private data class Form(
        val kind: EntryKind = EntryKind.EXPENSE,
        val selectedAccountId: String? = null,
        val amount: AmountInput = AmountInput.empty(),
        val saving: Boolean = false,
    )

    private val form = MutableStateFlow(Form(selectedAccountId = initialAccountId))

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    val uiState: StateFlow<QuickEntryUiState> = combine(
        form,
        accountRepository.observeActive(),
        currencyRepository.observeAll(),
    ) { f, accounts, currencies ->
        val byCode = currencies.associateBy { it.code.code }
        val options = accounts.map { account ->
            QuickAccountOption(
                id = account.id,
                name = account.name,
                currency = byCode[account.currency.code]
                    ?: Currency(account.currency, minorUnits = 2, displayScale = 2, symbol = account.currency.code),
            )
        }
        val selectedId = f.selectedAccountId?.takeIf { id -> options.any { it.id == id } } ?: options.firstOrNull()?.id
        QuickEntryUiState(
            kind = f.kind,
            categoryName = categoryName,
            accounts = options,
            selectedAccountId = selectedId,
            amount = f.amount,
            loaded = true,
            saving = f.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickEntryUiState(categoryName = categoryName))

    fun setKind(kind: EntryKind) {
        consumeMessage() // новая попытка — старая ошибка сохранения больше не актуальна
        form.update { it.copy(kind = kind, amount = AmountInput.empty()) }
    }

    /** Счёт для именно этой операции — не пишется в `WidgetConfigStore` (R4.2). */
    fun selectAccount(id: String) {
        consumeMessage()
        val newCurrency = uiState.value.accounts.firstOrNull { it.id == id }?.currency
        form.update { f ->
            val amount = if (newCurrency != null) AmountInput.fromText(f.amount.displayText(), newCurrency) else f.amount
            f.copy(selectedAccountId = id, amount = amount)
        }
    }

    fun pressDigit(digit: Char) {
        consumeMessage()
        val currency = uiState.value.currency ?: return
        form.update { it.copy(amount = it.amount.appendDigit(digit, currency)) }
    }

    fun pressDecimalPoint() {
        consumeMessage()
        val currency = uiState.value.currency ?: return
        form.update { it.copy(amount = it.amount.appendDecimalPoint(currency)) }
    }

    fun pressBackspace() {
        consumeMessage()
        form.update { it.copy(amount = it.amount.backspace()) }
    }

    /** @return false, если форма невалидна (пустая сумма/нет счёта) или запись уже идёт. */
    fun save(): Boolean {
        val state = uiState.value
        if (!state.canSave || state.saving) return false
        val currency = state.currency ?: return false
        val accountId = state.selectedAccountId ?: return false
        val minor = state.amount.toMinorOrNull(currency) ?: return false
        val money = Money(minor, currency.code)
        val kind = state.kind
        form.update { it.copy(saving = true) }
        launchWrite(
            onError = { form.update { f -> f.copy(saving = false) }; "Не удалось сохранить операцию" },
            onSuccess = { _finished.value = true },
        ) {
            when (kind) {
                EntryKind.EXPENSE -> txnRepository.addExpense(accountId, money, categoryId, today(), null)
                EntryKind.INCOME -> txnRepository.addIncome(accountId, money, categoryId, today(), null)
            }
        }
        return true
    }

    companion object {
        fun factory(
            txnRepository: TxnRepository,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            categoryId: String?,
            categoryName: String,
            initialAccountId: String?,
        ) = viewModelFactory {
            initializer {
                QuickEntryViewModel(
                    txnRepository, accountRepository, currencyRepository,
                    categoryId, categoryName, initialAccountId,
                )
            }
        }
    }
}
