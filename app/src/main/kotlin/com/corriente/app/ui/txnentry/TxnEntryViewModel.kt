package com.corriente.app.ui.txnentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
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
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Расход или доход. Перевод — отдельный поток ввода (T2.1). */
enum class EntryKind { EXPENSE, INCOME }

/** Счёт как вариант выбора в форме — с уже разрешённой [Currency] для клавиатуры и показа. */
data class AccountOption(val id: String, val name: String, val currency: Currency)

data class TxnEntryUiState(
    val kind: EntryKind = EntryKind.EXPENSE,
    val amount: AmountInput = AmountInput.empty(),
    val accounts: List<AccountOption> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
) {
    val selectedAccount: AccountOption? get() = accounts.firstOrNull { it.id == selectedAccountId }
    val currency: Currency? get() = selectedAccount?.currency
    val amountText: String get() = amount.displayText()

    /** I-1: знак не вводится; проверяем лишь, что сумма положительна и счёт выбран. */
    val canSave: Boolean
        get() {
            val currency = currency ?: return false
            val minor = amount.toMinorOrNull(currency) ?: return false
            return minor.raw > 0
        }
}

private fun entryKindToCategoryKind(kind: EntryKind): CategoryKind = when (kind) {
    EntryKind.EXPENSE -> CategoryKind.EXPENSE
    EntryKind.INCOME -> CategoryKind.INCOME
}

class TxnEntryViewModel(
    private val txns: TxnRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val currencies: CurrencyRepository,
    private val editingTxnId: String? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private data class Form(
        val kind: EntryKind = EntryKind.EXPENSE,
        val amount: AmountInput = AmountInput.empty(),
        val selectedAccountId: String? = null,
        val selectedCategoryId: String? = null,
        val date: LocalDate,
        val note: String = "",
    )

    private val form = MutableStateFlow(Form(date = today()))

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    val isEditing: Boolean get() = editingTxnId != null

    init {
        if (editingTxnId != null) {
            viewModelScope.launch {
                val existing = txns.getById(editingTxnId) ?: return@launch
                val (kind, accountId, amount, categoryId) = when (existing) {
                    is Txn.Expense -> Quad(EntryKind.EXPENSE, existing.accountId, existing.amount, existing.categoryId)
                    is Txn.Income -> Quad(EntryKind.INCOME, existing.accountId, existing.amount, existing.categoryId)
                    is Txn.Transfer -> return@launch // переводы здесь не редактируются
                }
                val currency = currencies.getByCode(amount.currency)
                    ?: Currency(amount.currency, minorUnits = 2, displayScale = 2, symbol = amount.currency.code)
                form.value = Form(
                    kind = kind,
                    amount = AmountInput.fromMinor(amount.amount, currency),
                    selectedAccountId = accountId,
                    selectedCategoryId = categoryId,
                    date = existing.date,
                    note = existing.note.orEmpty(),
                )
            }
        }
    }

    private data class Quad(
        val kind: EntryKind,
        val accountId: String,
        val amount: Money,
        val categoryId: String?,
    )

    val uiState: StateFlow<TxnEntryUiState> = combine(
        form,
        accounts.observeActive(),
        currencies.observeAll(),
        categories.observeActive(),
    ) { f, activeAccounts, allCurrencies, activeCategories ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val options = activeAccounts.map { account ->
            AccountOption(
                id = account.id,
                name = account.name,
                currency = byCode[account.currency.code]
                    ?: Currency(account.currency, minorUnits = 2, displayScale = 2, symbol = account.currency.code),
            )
        }
        val selectedAccountId = f.selectedAccountId?.takeIf { id -> options.any { it.id == id } }
            ?: options.firstOrNull()?.id
        val kindCategories = activeCategories.filter { it.kind == entryKindToCategoryKind(f.kind) }
        TxnEntryUiState(
            kind = f.kind,
            amount = f.amount,
            accounts = options,
            selectedAccountId = selectedAccountId,
            categories = kindCategories,
            selectedCategoryId = f.selectedCategoryId?.takeIf { id -> kindCategories.any { it.id == id } },
            date = f.date,
            note = f.note,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnEntryUiState(date = form.value.date))

    fun setKind(kind: EntryKind) = form.update { it.copy(kind = kind, selectedCategoryId = null) }

    fun pressDigit(digit: Char) {
        val currency = uiState.value.currency ?: return
        form.update { it.copy(amount = it.amount.appendDigit(digit, currency)) }
    }

    fun pressDecimalPoint() {
        val currency = uiState.value.currency ?: return
        form.update { it.copy(amount = it.amount.appendDecimalPoint(currency)) }
    }

    fun pressBackspace() = form.update { it.copy(amount = it.amount.backspace()) }

    fun selectAccount(id: String) {
        val newCurrency = uiState.value.accounts.firstOrNull { it.id == id }?.currency
        form.update { f ->
            val amount = if (newCurrency != null) AmountInput.fromText(f.amount.displayText(), newCurrency) else f.amount
            f.copy(selectedAccountId = id, amount = amount)
        }
    }

    fun selectCategory(id: String?) = form.update { it.copy(selectedCategoryId = id) }

    fun selectDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun setNote(note: String) = form.update { it.copy(note = note) }

    /** @return false, если форма невалидна. При успехе выставляет [finished] после записи. */
    fun save(): Boolean {
        val state = uiState.value
        if (!state.canSave) return false
        val currency = state.currency ?: return false
        val accountId = state.selectedAccountId ?: return false
        val money = Money(state.amount.toMinorOrNull(currency)!!, currency.code)
        val note = state.note.trim().ifBlank { null }
        viewModelScope.launch {
            if (editingTxnId != null) {
                txns.updateEntry(editingTxnId, accountId, money, state.selectedCategoryId, state.date, note)
            } else {
                when (state.kind) {
                    EntryKind.EXPENSE -> txns.addExpense(accountId, money, state.selectedCategoryId, state.date, note)
                    EntryKind.INCOME -> txns.addIncome(accountId, money, state.selectedCategoryId, state.date, note)
                }
            }
            _finished.value = true
        }
        return true
    }

    fun deleteEditing() {
        val id = editingTxnId ?: return
        viewModelScope.launch {
            txns.deleteById(id)
            _finished.value = true
        }
    }

    companion object {
        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            editingTxnId: String? = null,
        ) = viewModelFactory {
            initializer { TxnEntryViewModel(txns, accounts, categories, currencies, editingTxnId) }
        }
    }
}
