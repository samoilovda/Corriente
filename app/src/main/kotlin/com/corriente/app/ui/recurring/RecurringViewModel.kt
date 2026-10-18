package com.corriente.app.ui.recurring

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.R
import com.corriente.app.ui.common.InvalidInputException
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.amountTextIsPositive
import com.corriente.app.ui.common.uiMessage
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Строка списка на экране «Повторяющиеся» (R2.4). Название категории/счёта — данные
 * пользователя (не локализуются), но вид/категория-по-умолчанию/правило — UI-текст,
 * поэтому строка отдаёт вызывающему сырые значения, а не готовую фразу: собирает её
 * Composable через `stringResource` (R6.3, ROADMAP.md §8 — ViewModel ничего не знает
 * про текущую локаль).
 */
data class RecurringRow(
    val id: String,
    val kind: TxnKind,
    val categoryName: String?,
    val accountName: String?,
    val amountText: String,
    val rule: RecurrenceRule,
    val nextRunText: String,
    /** A1.5: последняя ошибка материализации этого правила (null — всё штатно). */
    val lastError: String? = null,
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
    /** Запись в полёте — диалог обязан отключить «Сохранить» на это время: без гварда двойной
     *  тап до закрытия диалога (`editorState.value = null` приходит только по onSuccess) успевал
     *  создать два одинаковых правила. */
    val saving: Boolean = false,
) {
    /** A2.3: «Сохранить» активна только при положительной сумме и корректном дне месяца / интервале. */
    val canSave: Boolean
        get() = amountTextIsPositive(amountText) && when (ruleType) {
            RecurrenceRuleType.DAY_OF_MONTH -> dayOfMonthText.toIntOrNull() in 1..31
            RecurrenceRuleType.EVERY_N_DAYS -> (intervalDaysText.toIntOrNull() ?: 0) >= 1
        }
}

/** U12: ближайшая запись правила в учёте — не платёж в банке. */
data class UpcomingRow(
    val ruleId: String,
    val date: LocalDate,
    val kind: TxnKind,
    val categoryName: String?,
    val accountName: String?,
    val amountText: String,
    /** Дата уже прошла, воркер запишет при ближайшем запуске. */
    val pending: Boolean,
)

data class RecurringUiState(
    val rows: List<RecurringRow> = emptyList(),
    /** U12: что правила запишут за [UPCOMING_HORIZON_DAYS] дней. */
    val upcoming: List<UpcomingRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val editor: RecurringEditor? = null,
)

/** U12: горизонт «ближайших» и сколько строк показывать максимум. */
const val UPCOMING_HORIZON_DAYS = 30L
private const val UPCOMING_LIMIT = 20

private fun fallbackCurrency(code: String): Currency = Currency(CurrencyCode(code), minorUnits = 2, displayScale = 2, symbol = code)

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
    errors: Flow<Map<String, String>> = flowOf(emptyMap()),
) : WritingViewModel() {

    private val editorState = MutableStateFlow<RecurringEditor?>(null)

    val uiState: StateFlow<RecurringUiState> = combine(
        recurrenceRepository.observeAll(),
        accountRepository.observeAll(),
        categoryRepository.observeAllForLookup(),
        currencyRepository.observeAll(),
        combine(editorState, errors) { editor, errs -> editor to errs },
    ) { allRecurrences, allAccounts, allCategories, allCurrencies, editorAndErrors ->
        val (editor, ruleErrors) = editorAndErrors
        val byCode = allCurrencies.associateBy { it.code.code }
        val accountNames = allAccounts.associate { it.id to it.name }
        val categoryNames = allCategories.associate { it.id to it.name }
        val rows = allRecurrences.map { r ->
            val currency = byCode[r.amount.currency.code] ?: fallbackCurrency(r.amount.currency.code)
            RecurringRow(
                id = r.id,
                kind = r.kind,
                categoryName = r.categoryId?.let { categoryNames[it] },
                accountName = accountNames[r.accountId],
                amountText = MoneyFormatter.format(r.amount, currency),
                rule = r.rule,
                nextRunText = r.nextRunOn.toString(),
                lastError = ruleErrors[r.id],
            )
        }
        val archivedAccounts = allAccounts.filter { it.isArchived }.mapTo(HashSet()) { it.id }
        val upcoming = com.corriente.data.recurrence.upcomingOccurrences(
            allRecurrences, today(), UPCOMING_HORIZON_DAYS, isAccountArchived = { it in archivedAccounts },
        ).take(UPCOMING_LIMIT).map { o ->
            val r = o.recurrence
            val currency = byCode[r.amount.currency.code] ?: fallbackCurrency(r.amount.currency.code)
            UpcomingRow(
                ruleId = r.id,
                date = o.date,
                kind = r.kind,
                categoryName = r.categoryId?.let { categoryNames[it] },
                accountName = accountNames[r.accountId],
                amountText = MoneyFormatter.format(r.amount, currency),
                pending = o.pending,
            )
        }
        RecurringUiState(
            rows = rows,
            upcoming = upcoming,
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
        if (editor.saving) return
        val accountId = editor.accountId ?: return
        editorState.update { it?.copy(saving = true) }
        launchWrite(
            onError = { e ->
                editorState.update { ed -> ed?.copy(saving = false) }
                (e as? InvalidInputException)?.ui ?: uiMessage(R.string.recurring_error_save)
            },
            onSuccess = { editorState.value = null },
        ) {
            val account = requireNotNull(accountRepository.getById(accountId)) { "Account $accountId not found" }
            val currencyMeta = currencyRepository.getByCode(account.currency) ?: fallbackCurrency(account.currency.code)
            val minor = AmountInput.fromText(editor.amountText, currencyMeta).toMinorOrNull(currencyMeta)
            if (minor == null || minor.raw <= 0) {
                throw InvalidInputException(uiMessage(R.string.recurring_error_invalid_amount))
            }
            val money = Money(minor, account.currency)
            val rule = when (editor.ruleType) {
                RecurrenceRuleType.DAY_OF_MONTH ->
                    RecurrenceRule.DayOfMonth(
                        editor.dayOfMonthText.toIntOrNull()?.takeIf { it in 1..31 }
                            ?: throw InvalidInputException(uiMessage(R.string.recurring_error_invalid_day)),
                    )
                RecurrenceRuleType.EVERY_N_DAYS ->
                    RecurrenceRule.EveryNDays(
                        editor.intervalDaysText.toIntOrNull()?.takeIf { it >= 1 }
                            ?: throw InvalidInputException(uiMessage(R.string.recurring_error_invalid_interval)),
                    )
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
        launchWrite(onError = { uiMessage(R.string.recurring_error_delete) }) { recurrenceRepository.delete(id) }
    }

    companion object {
        fun factory(
            recurrences: RecurrenceRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            errors: Flow<Map<String, String>> = flowOf(emptyMap()),
        ) = viewModelFactory {
            initializer { RecurringViewModel(recurrences, accounts, categories, currencies, errors = errors) }
        }
    }
}

/** Категории вида [kind] для выпадающего списка в форме (та же связка Kind<->CategoryKind, что в TxnEntry). */
internal fun categoriesFor(kind: TxnKind, all: List<Category>): List<Category> {
    val categoryKind = if (kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    return all.filter { it.kind == categoryKind }
}
