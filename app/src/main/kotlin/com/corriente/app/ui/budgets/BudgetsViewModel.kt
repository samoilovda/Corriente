package com.corriente.app.ui.budgets

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.R
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.uiMessage
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Category
import com.corriente.data.repository.BudgetRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
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

/** Строка списка на экране «Бюджеты» (R2.3). */
data class BudgetRow(
    val id: String,
    /** null — бюджет «На всё» (без категории); текст ресурса подставляет экран. */
    val categoryName: String?,
    val amountText: String,
)

/** Форма создания/редактирования бюджета — значения ровно как их ввёл пользователь. */
data class BudgetEditor(
    val editingId: String? = null,
    /** null — бюджет «на всё» (сумма всех категорий), а не «на все деньги сразу» (ADR-012). */
    val categoryId: String? = null,
    val currencyCode: String,
    val amountText: String = "",
)

data class BudgetsUiState(
    val rows: List<BudgetRow> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val currencyCodes: List<String> = emptyList(),
    val editor: BudgetEditor? = null,
)

internal fun fallbackCurrency(code: String): Currency =
    Currency(CurrencyCode(code), minorUnits = 2, displayScale = 2, symbol = code)

/**
 * Минорные единицы → "1234.56" без символа и группировки, для поля ввода формы (I-25) —
 * то же самое, что [com.corriente.app.ui.accounts.openingBalanceText], но без знака: сумма
 * бюджета всегда неотрицательна (проверяется в [BudgetRepository]).
 */
internal fun plainAmountText(money: Money, minorUnits: Int): String {
    if (money.amount.raw == 0L) return ""
    val digits = money.amount.raw.toString().padStart(minorUnits + 1, '0')
    return if (minorUnits == 0) digits else "${digits.dropLast(minorUnits)}.${digits.takeLast(minorUnits)}"
}

/**
 * Бюджеты по категориям (R2.3). MONTH — единственный период, поэтому в форме его вообще не
 * показываем: усложнять UI ради значения, которого нет, — лишняя абстракция (BUILD_PLAN.md §0).
 */
class BudgetsViewModel(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    private val editorState = MutableStateFlow<BudgetEditor?>(null)

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetRepository.observeAll(),
        categoryRepository.observeActive(),
        currencyRepository.observeActive(),
        editorState,
    ) { allBudgets, allCategories, allCurrencies, editor ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val namesById = allCategories.associate { it.id to it.name }
        val rows = allBudgets
            .map { b ->
                val currency = byCode[b.amount.currency.code] ?: fallbackCurrency(b.amount.currency.code)
                BudgetRow(
                    id = b.id,
                    categoryName = b.categoryId?.let { namesById[it] },
                    amountText = MoneyFormatter.format(b.amount, currency),
                )
            }
            // R6.3: "На всё"/"For everything" — UI-текст ресурса, а не значение для сортировки;
            // сортируем по сырому имени категории, бюджет без категории — первым.
            .sortedBy { it.categoryName ?: "" }
        BudgetsUiState(
            rows = rows,
            expenseCategories = allCategories.filter { it.kind == CategoryKind.EXPENSE },
            currencyCodes = allCurrencies.map { it.code.code },
            editor = editor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun startCreate() {
        val code = uiState.value.currencyCodes.firstOrNull() ?: return
        editorState.value = BudgetEditor(currencyCode = code)
    }

    fun startEdit(id: String) {
        viewModelScope.launch {
            val budget = budgetRepository.getById(id) ?: return@launch
            val currencyMeta = currencyRepository.getByCode(budget.amount.currency) ?: fallbackCurrency(budget.amount.currency.code)
            editorState.value = BudgetEditor(
                editingId = budget.id,
                categoryId = budget.categoryId,
                currencyCode = budget.amount.currency.code,
                amountText = plainAmountText(budget.amount, currencyMeta.minorUnits),
            )
        }
    }

    fun cancelEdit() {
        editorState.value = null
    }

    fun setCategory(categoryId: String?) = editorState.update { it?.copy(categoryId = categoryId) }

    fun setCurrency(code: String) = editorState.update { it?.copy(currencyCode = code) }

    fun setAmountText(text: String) = editorState.update { it?.copy(amountText = text) }

    fun save() {
        val editor = editorState.value ?: return
        launchWrite(onError = { uiMessage(R.string.budgets_error_save) }, onSuccess = { editorState.value = null }) {
            val currencyMeta = currencyRepository.getByCode(CurrencyCode(editor.currencyCode)) ?: fallbackCurrency(editor.currencyCode)
            val minor = AmountInput.fromText(editor.amountText, currencyMeta).toMinorOrNull(currencyMeta) ?: return@launchWrite
            val money = Money(minor, CurrencyCode(editor.currencyCode))
            if (editor.editingId != null) {
                budgetRepository.update(editor.editingId, editor.categoryId, money, today())
            } else {
                budgetRepository.create(editor.categoryId, money, today())
            }
        }
    }

    fun delete(id: String) {
        launchWrite(onError = { uiMessage(R.string.budgets_error_delete) }) { budgetRepository.delete(id) }
    }

    companion object {
        fun factory(
            budgets: BudgetRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
        ) = viewModelFactory {
            initializer { BudgetsViewModel(budgets, categories, currencies) }
        }
    }
}
