package com.corriente.app.ui.txnentry

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.AmountInput
import com.corriente.money.CalcOp
import com.corriente.money.Currency
import com.corriente.money.Minor
import com.corriente.money.Money
import com.corriente.money.applyCalc
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
data class AccountOption(val id: String, val name: String, val currency: Currency, val isArchived: Boolean = false)

data class TxnEntryUiState(
    val kind: EntryKind = EntryKind.EXPENSE,
    val amount: AmountInput = AmountInput.empty(),
    val calcAcc: Long? = null,
    val calcOp: CalcOp? = null,
    val accounts: List<AccountOption> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** F2.5: false до первой эмиссии репозиториев — экран не показывает ни форму, ни «нет счетов». */
    val loaded: Boolean = false,
) {
    val selectedAccount: AccountOption? get() = accounts.firstOrNull { it.id == selectedAccountId }
    val currency: Currency? get() = selectedAccount?.currency

    /** F2.8: калькулятор посчитал результат ≤ 0 — держим его, не обнуляем молча. */
    val heldResult: Long? get() = if (calcOp == null && calcAcc != null && amount.isEmpty) calcAcc else null

    /** Показ поля суммы: «1250 + 320», пока операция не завершена. */
    val amountText: String
        get() {
            val currency = currency ?: return amount.displayText()
            heldResult?.let { return majorText(it, currency) }
            val operand = amount.displayText()
            if (calcAcc == null || calcOp == null) return operand
            return "${majorText(calcAcc, currency)} ${calcOp.symbol} $operand"
        }

    /**
     * Отображение накопителя калькулятора. Он может быть отрицательным (напр. «5 − 10» и ещё «− 3»),
     * а [AmountInput.fromMinor] принимает только неотрицательное (знак — в типе операции, I-1),
     * поэтому знак выносим в строку сами.
     */
    private fun majorText(minor: Long, currency: Currency): String {
        val magnitude = AmountInput.fromMinor(Minor(kotlin.math.abs(minor)), currency).displayText()
        return if (minor < 0) "−$magnitude" else magnitude
    }

    val hasPendingCalc: Boolean get() = calcAcc != null && calcOp != null

    /** Итоговая сумма с учётом незавершённой операции калькулятора. */
    fun resolvedMinor(): Minor? {
        val currency = currency ?: return null
        heldResult?.let { return Minor(it) }
        val operand = amount.toMinorOrNull(currency) ?: if (hasPendingCalc) Minor(0) else return null
        return if (calcAcc != null && calcOp != null) applyCalc(Minor(calcAcc), calcOp, operand) else operand
    }

    /** I-1: знак не вводится; проверяем лишь, что сумма положительна и счёт выбран. */
    val canSave: Boolean
        get() {
            if (currency == null) return false
            return (resolvedMinor()?.raw ?: 0) > 0
        }

    /** F2.8: показать подсказку «сумма должна быть больше нуля», когда результат ≤ 0. */
    val nonPositiveResult: Boolean get() = resolvedMinor()?.let { it.raw <= 0 } == true
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
    initialKind: EntryKind = EntryKind.EXPENSE,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    private data class Form(
        val kind: EntryKind,
        val amount: AmountInput = AmountInput.empty(),
        val calcAcc: Long? = null,
        val calcOp: CalcOp? = null,
        val selectedAccountId: String? = null,
        val selectedCategoryId: String? = null,
        val date: LocalDate,
        val note: String = "",
    )

    private val form = MutableStateFlow(Form(kind = initialKind, date = today()))

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
        accounts.observeAll(),
        currencies.observeAll(),
        categories.observeAllForLookup(),
    ) { f, allAccounts, allCurrencies, allCategories ->
        val byCode = allCurrencies.associateBy { it.code.code }
        fun option(account: Account) = AccountOption(
            id = account.id,
            name = account.name,
            currency = byCode[account.currency.code]
                ?: Currency(account.currency, minorUnits = 2, displayScale = 2, symbol = account.currency.code),
            isArchived = account.isArchived,
        )
        // F0.3: варианты — активные счета плюс архивный счёт редактируемой операции, чтобы
        // правка заметки/суммы не переносила операцию на чужой счёт.
        val editingAccountId = if (editingTxnId != null) f.selectedAccountId else null
        val options = (
            allAccounts.filterNot { it.isArchived } +
                allAccounts.filter { it.isArchived && it.id == editingAccountId }
            ).map(::option)
        val selectedAccountId = when {
            f.selectedAccountId != null && options.any { it.id == f.selectedAccountId } -> f.selectedAccountId
            editingTxnId == null -> options.firstOrNull()?.id
            else -> null // редактируем, а счёт операции удалён совсем — сохранение заблокировано
        }
        // F0.4: активные категории того же вида плюс архивная категория редактируемой операции,
        // чтобы правка суммы не обнуляла категорию.
        val editingCategoryId = if (editingTxnId != null) f.selectedCategoryId else null
        val kindCategories = allCategories.filter {
            it.kind == entryKindToCategoryKind(f.kind) && (!it.isArchived || it.id == editingCategoryId)
        }
        TxnEntryUiState(
            kind = f.kind,
            amount = f.amount,
            calcAcc = f.calcAcc,
            calcOp = f.calcOp,
            accounts = options,
            selectedAccountId = selectedAccountId,
            categories = kindCategories,
            selectedCategoryId = f.selectedCategoryId?.takeIf { id -> kindCategories.any { it.id == id } },
            date = f.date,
            note = f.note,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnEntryUiState(date = form.value.date))

    fun setKind(kind: EntryKind) = form.update { it.copy(kind = kind, selectedCategoryId = null) }

    /** F2.8: набор новой цифры после удержанного результата ≤ 0 начинает ввод заново. */
    private fun Form.clearHeldResult(): Form =
        if (calcOp == null && calcAcc != null) copy(calcAcc = null) else this

    fun pressDigit(digit: Char) {
        val currency = uiState.value.currency ?: return
        form.update { it.clearHeldResult().let { f -> f.copy(amount = f.amount.appendDigit(digit, currency)) } }
    }

    fun pressDecimalPoint() {
        val currency = uiState.value.currency ?: return
        form.update { it.clearHeldResult().let { f -> f.copy(amount = f.amount.appendDecimalPoint(currency)) } }
    }

    fun pressBackspace() = form.update { it.clearHeldResult().let { f -> f.copy(amount = f.amount.backspace()) } }

    /** T5.5: калькулятор — «+»/«−». Завершает текущий операнд и начинает новый. */
    fun pressOp(op: CalcOp) {
        val currency = uiState.value.currency ?: return
        form.update { f ->
            val operand = f.amount.toMinorOrNull(currency) ?: Minor(f.calcAcc ?: 0)
            val newAcc = if (f.calcAcc != null && f.calcOp != null) {
                applyCalc(Minor(f.calcAcc), f.calcOp, operand)
            } else {
                operand
            }
            f.copy(amount = AmountInput.empty(), calcAcc = newAcc.raw, calcOp = op)
        }
    }

    fun pressEquals() {
        val currency = uiState.value.currency ?: return
        form.update { f ->
            if (f.calcAcc == null || f.calcOp == null) return@update f
            val operand = f.amount.toMinorOrNull(currency) ?: Minor(0)
            val result = applyCalc(Minor(f.calcAcc), f.calcOp, operand)
            // F2.8: результат ≤ 0 — держим как calcAcc без операции, показываем «−5» и блокируем
            // «Сохранить» с подсказкой; продолжение «+ 20 =» даёт «15».
            if (result.raw > 0) {
                f.copy(amount = AmountInput.fromMinor(result, currency), calcAcc = null, calcOp = null)
            } else {
                f.copy(amount = AmountInput.empty(), calcAcc = result.raw, calcOp = null)
            }
        }
    }

    fun selectAccount(id: String) {
        val newCurrency = uiState.value.accounts.firstOrNull { it.id == id }?.currency
        form.update { f ->
            val amount = if (newCurrency != null) AmountInput.fromText(f.amount.displayText(), newCurrency) else f.amount
            f.copy(selectedAccountId = id, amount = amount, calcAcc = null, calcOp = null)
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
        val money = Money(state.resolvedMinor()!!, currency.code)
        val note = state.note.trim().ifBlank { null }
        launchWrite(
            onError = { "Не удалось сохранить операцию" },
            onSuccess = { _finished.value = true },
        ) {
            if (editingTxnId != null) {
                txns.updateEntry(editingTxnId, accountId, money, state.selectedCategoryId, state.date, note)
            } else {
                when (state.kind) {
                    EntryKind.EXPENSE -> txns.addExpense(accountId, money, state.selectedCategoryId, state.date, note)
                    EntryKind.INCOME -> txns.addIncome(accountId, money, state.selectedCategoryId, state.date, note)
                }
            }
        }
        return true
    }

    fun deleteEditing() {
        val id = editingTxnId ?: return
        launchWrite(
            onError = { "Не удалось удалить операцию" },
            onSuccess = { _finished.value = true },
        ) {
            txns.deleteById(id)
        }
    }

    companion object {
        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            editingTxnId: String? = null,
            initialKind: EntryKind = EntryKind.EXPENSE,
        ) = viewModelFactory {
            initializer { TxnEntryViewModel(txns, accounts, categories, currencies, editingTxnId, initialKind) }
        }
    }
}
