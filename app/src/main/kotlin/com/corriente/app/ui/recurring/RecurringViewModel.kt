package com.corriente.app.ui.recurring

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.RecurrenceRepository
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Строка списка на экране «Повторяющиеся» (R2.4). */
data class RecurringRow(
    val id: String,
    val title: String,
    val amountText: String,
    val ruleText: String,
    val nextRunText: String,
)

/** Форма создания/редактирования правила — значения ровно как их ввёл пользователь. */
data class RecurringEditor(
    val editingId: String? = null,
    val kind: TxnKind = TxnKind.EXPENSE,
    val accountId: String? = null,
    val categoryId: String? = null,
    val amountText: String = "",
    val note: String = "",
    val ruleType: RecurrenceRuleType = RecurrenceRuleType.DAY_OF_MONTH,
    val dayOfMonthText: String = "1",
    val intervalDaysText: String = "30",
)

data class RecurringUiState(
    val rows: List<RecurringRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val editor: RecurringEditor? = null,
)

private fun fallbackCurrency(code: String): Currency = Currency(CurrencyCode(code), minorUnits = 2, displayScale = 2, symbol = code)

private fun ruleText(rule: RecurrenceRule): String = when (rule) {
    is RecurrenceRule.DayOfMonth -> "${rule.day}-го числа каждого месяца"
    is RecurrenceRule.EveryNDays -> "Каждые ${rule.intervalDays} дн."
}

/**
 * Повторяющиеся операции (R2.4). Перевод не поддерживается (см. [RecurrenceRepository]) — форма
 * предлагает только расход/доход.
 */
class RecurringViewModel(
    private val recurrenceRepository: RecurrenceRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    private val editorState = MutableStateFlow<RecurringEditor?>(null)

    val uiState: StateFlow<RecurringUiState> = combine(
        recurrenceRepository.observeAll(),
        accountRepository.observeAll(),
        categoryRepository.observeAllForLookup(),
        currencyRepository.observeAll(),
        editorState,
    ) { allRecurrences, allAccounts, allCategories, allCurrencies, editor ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val accountNames = allAccounts.associate { it.id to it.name }
        val categoryNames = allCategories.associate { it.id to it.name }
        val rows = allRecurrences.map { r ->
            val currency = byCode[r.amount.currency.code] ?: fallbackCurrency(r.amount.currency.code)
            val kindLabel = if (r.kind == TxnKind.INCOME) "Доход" else "Расход"
            val categoryLabel = r.categoryId?.let { categoryNames[it] } ?: "Без категории"
            RecurringRow(
                id = r.id,
                title = "$kindLabel: $categoryLabel · ${accountNames[r.accountId] ?: "?"}",
                amountText = MoneyFormatter.format(r.amount, currency),
                ruleText = ruleText(r.rule),
                nextRunText = r.nextRunOn.toString(),
            )
        }
        RecurringUiState(
            rows = rows,
            accounts = allAccounts.filterNot { it.isArchived },
            categories = allCategories.filterNot { it.isArchived },
            editor = editor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun startCreate() {
        val accountId = uiState.value.accounts.firstOrNull()?.id ?: return
        editorState.value = RecurringEditor(accountId = accountId)
    }

    fun startEdit(id: String) {
        viewModelScope.launch {
            val r = recurrenceRepository.getById(id) ?: return@launch
            editorState.value = RecurringEditor(
                editingId = r.id,
                kind = r.kind,
                accountId = r.accountId,
                categoryId = r.categoryId,
                amountText = plainAmountText(r.amount, currencyRepository),
                note = r.note.orEmpty(),
                ruleType = when (r.rule) {
                    is RecurrenceRule.DayOfMonth -> RecurrenceRuleType.DAY_OF_MONTH
                    is RecurrenceRule.EveryNDays -> RecurrenceRuleType.EVERY_N_DAYS
                },
                dayOfMonthText = (r.rule as? RecurrenceRule.DayOfMonth)?.day?.toString() ?: "1",
                intervalDaysText = (r.rule as? RecurrenceRule.EveryNDays)?.intervalDays?.toString() ?: "30",
            )
        }
    }

    private suspend fun plainAmountText(money: Money, currencies: CurrencyRepository): String {
        val minorUnits = currencies.getByCode(money.currency)?.minorUnits ?: 2
        if (money.amount.raw == 0L) return ""
        val digits = money.amount.raw.toString().padStart(minorUnits + 1, '0')
        return if (minorUnits == 0) digits else "${digits.dropLast(minorUnits)}.${digits.takeLast(minorUnits)}"
    }

    fun cancelEdit() {
        editorState.value = null
    }

    fun setKind(kind: TxnKind) = editorState.update { it?.copy(kind = kind, categoryId = null) }

    fun setAccount(accountId: String) = editorState.update { it?.copy(accountId = accountId) }

    fun setCategory(categoryId: String?) = editorState.update { it?.copy(categoryId = categoryId) }

    fun setAmountText(text: String) = editorState.update { it?.copy(amountText = text) }

    fun setNote(text: String) = editorState.update { it?.copy(note = text) }

    fun setRuleType(type: RecurrenceRuleType) = editorState.update { it?.copy(ruleType = type) }

    fun setDayOfMonthText(text: String) = editorState.update { it?.copy(dayOfMonthText = text) }

    fun setIntervalDaysText(text: String) = editorState.update { it?.copy(intervalDaysText = text) }

    fun save() {
        val editor = editorState.value ?: return
        val accountId = editor.accountId ?: return
        launchWrite(onError = { "Не удалось сохранить правило" }, onSuccess = { editorState.value = null }) {
            val account = requireNotNull(accountRepository.getById(accountId)) { "Account $accountId not found" }
            val currencyMeta = currencyRepository.getByCode(account.currency) ?: fallbackCurrency(account.currency.code)
            val minor = AmountInput.fromText(editor.amountText, currencyMeta).toMinorOrNull(currencyMeta) ?: return@launchWrite
            val money = Money(minor, account.currency)
            val rule = when (editor.ruleType) {
                RecurrenceRuleType.DAY_OF_MONTH ->
                    RecurrenceRule.DayOfMonth(editor.dayOfMonthText.toIntOrNull()?.coerceIn(1, 31) ?: return@launchWrite)
                RecurrenceRuleType.EVERY_N_DAYS ->
                    RecurrenceRule.EveryNDays(editor.intervalDaysText.toIntOrNull()?.coerceAtLeast(1) ?: return@launchWrite)
            }
            val note = editor.note.trim().ifBlank { null }
            if (editor.editingId != null) {
                recurrenceRepository.update(editor.editingId, accountId, editor.categoryId, money, note, rule, today())
            } else {
                recurrenceRepository.create(editor.kind, accountId, editor.categoryId, money, note, rule, today())
            }
        }
    }

    fun delete(id: String) {
        launchWrite(onError = { "Не удалось удалить правило" }) { recurrenceRepository.delete(id) }
    }

    companion object {
        fun factory(
            recurrences: RecurrenceRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
        ) = viewModelFactory {
            initializer { RecurringViewModel(recurrences, accounts, categories, currencies) }
        }
    }
}

/** Категории вида [kind] для выпадающего списка в форме (та же связка Kind<->CategoryKind, что в TxnEntry). */
internal fun categoriesFor(kind: TxnKind, all: List<Category>): List<Category> {
    val categoryKind = if (kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    return all.filter { it.kind == categoryKind }
}
