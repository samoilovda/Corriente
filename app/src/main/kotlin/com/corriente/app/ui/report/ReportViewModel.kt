package com.corriente.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.model.Txn
import com.corriente.data.repository.BudgetRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.CategoryTotal
import com.corriente.data.usecase.ReportKind
import com.corriente.data.usecase.budgetProgress
import com.corriente.data.usecase.categoryReport
import com.corriente.data.usecase.monthlySeries
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** F2.1: сколько месяцев назад от «якоря» строится график «по месяцам». */
private const val MONTHLY_SERIES_MONTHS = 6L

data class ReportRow(
    val categoryId: String?,
    val name: String,
    val amountText: String,
    val sharePercent: Int,
    val color: Int = 0,
)

/** T5.3: столбец графика «по месяцам» — значение уже посчитано (I-1), Float только в Canvas. */
data class MonthlyBar(val label: String, val valueMinor: Long, val amountText: String)

/** T5.3: доля категории для кольцевой диаграммы. */
data class CategorySlice(val name: String, val valueMinor: Long, val color: Int, val amountText: String)

data class TxnBrief(val id: String, val date: LocalDate, val amountText: String, val note: String?)

data class Drilldown(val categoryName: String, val txns: List<TxnBrief>)

/**
 * R2.3: полоса «потрачено из бюджета» под строкой категории. [percentClamped] уже зажат в
 * [0, 100] (I-1: целые проценты, не Float — `Float` появляется только на границе с
 * `LinearProgressIndicator`, внутри `ReportScreen.kt`, не здесь); факт перерасхода — отдельный
 * флаг [isOverBudget], не выводится из сравнения процентов.
 */
data class BudgetBar(
    val categoryId: String?,
    val spentText: String,
    val budgetText: String,
    val percentClamped: Int,
    val isOverBudget: Boolean,
)

data class ReportUiState(
    val kind: ReportKind = ReportKind.EXPENSE,
    val periodMode: PeriodMode = PeriodMode.MONTH,
    val periodLabel: String = "",
    val currencyCodes: List<String> = emptyList(),
    val selectedCurrency: String? = null,
    val rows: List<ReportRow> = emptyList(),
    val totalText: String? = null,
    val drilldown: Drilldown? = null,
    val monthly: List<MonthlyBar> = emptyList(),
    val slices: List<CategorySlice> = emptyList(),
    /**
     * Бюджеты категорий, ключ — `categoryId` (только для расходов, R2.3). Бюджет «на всё»
     * (`categoryId == null`) хранится отдельно от [ReportRow] с `categoryId == null` («Без
     * категории») — это разные вещи, которые иначе конфликтовали бы за один и тот же ключ.
     */
    val categoryBudgetBars: Map<String, BudgetBar> = emptyMap(),
    val wholeCurrencyBudgetBar: BudgetBar? = null,
)

internal fun withShares(
    report: List<CategoryTotal>,
    names: Map<String, String>,
    colors: Map<String, Int>,
    currency: Currency,
): List<ReportRow> {
    val grand = report.sumOf { it.total.amount.raw }
    // F3.6: доли по методу наибольших остатков — их сумма ровно 100 при непустом отчёте, а не 97–99.
    val shares = largestRemainderShares(report.map { it.total.amount.raw }, grand)
    return report.mapIndexed { i, total ->
        ReportRow(
            categoryId = total.categoryId,
            name = total.categoryId?.let { names[it] } ?: "Без категории",
            amountText = MoneyFormatter.format(total.total, currency),
            sharePercent = shares[i],
            color = total.categoryId?.let { colors[it] } ?: 0,
        )
    }
}

/**
 * Проценты от суммы [grand] по методу наибольших остатков: floor каждой доли плюс раздача
 * остатка (100 − сумма floor'ов) тем позициям, у которых дробная часть больше.
 * I-3: умножение через `Math.multiplyExact`.
 */
internal fun largestRemainderShares(values: List<Long>, grand: Long): List<Int> {
    if (grand <= 0L || values.isEmpty()) return List(values.size) { 0 }
    val scaled = values.map { Math.multiplyExact(it, 100L) }
    val floors = scaled.map { (it / grand).toInt() }.toIntArray()
    val leftover = (100 - floors.sum()).coerceIn(0, values.size)
    scaled.indices
        .sortedByDescending { scaled[it] % grand }
        .take(leftover)
        .forEach { floors[it] += 1 }
    return floors.toList()
}

class ReportViewModel(
    private val txns: TxnRepository,
    private val categories: CategoryRepository,
    private val currencies: CurrencyRepository,
    private val budgets: BudgetRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private data class Form(
        val kind: ReportKind = ReportKind.EXPENSE,
        val mode: PeriodMode = PeriodMode.MONTH,
        val anchor: LocalDate,
        val customStart: LocalDate,
        val customEnd: LocalDate,
        val currency: String? = null,
        val drilldownCategoryId: String? = null,
        val drilldownActive: Boolean = false,
    )

    private val form = MutableStateFlow(today().let { Form(anchor = it, customStart = it.withDayOfMonth(1), customEnd = it) })

    /** F2.1: диапазон, который реально нужен экрану — период отчёта плюс 6 месяцев назад для графика. */
    private fun queryRange(f: Form): Pair<LocalDate, LocalDate> {
        val range = periodRange(f.mode, f.anchor, f.customStart, f.customEnd)
        val monthlyStart = f.anchor.minusMonths(MONTHLY_SERIES_MONTHS).withDayOfMonth(1)
        return minOf(range.start, monthlyStart) to maxOf(range.endInclusive, today())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReportUiState> = form.flatMapLatest { f ->
        val (from, to) = queryRange(f)
        combine(
            txns.observeRange(from, to),
            categories.observeAllForLookup(),
            currencies.observeAll(),
            budgets.observeAll(),
        ) { allTxns, allCategories, allCurrencies, allBudgets ->
        val range = periodRange(f.mode, f.anchor, f.customStart, f.customEnd)
        val byCode = allCurrencies.associateBy { it.code.code }
        val names = allCategories.associate { it.id to it.name }
        val colors = allCategories.associate { it.id to it.color }
        val inPeriod = allTxns.filter { it.date in range }
        val currencyCodes = inPeriod
            .mapNotNull {
                when (it) {
                    is Txn.Expense -> it.amount.currency.code
                    is Txn.Income -> it.amount.currency.code
                    is Txn.Transfer -> null
                }
            }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val selected = f.currency?.takeIf { it in currencyCodes } ?: currencyCodes.firstOrNull()
        val currency = selected?.let { byCode[it] } ?: selected?.let { fallbackCurrency(it) }

        val report = if (selected == null) emptyList() else
            categoryReport(allTxns, CurrencyCode(selected), range, f.kind)
        val rows = if (currency == null) emptyList() else withShares(report, names, colors, currency)
        val total = if (report.isEmpty() || currency == null) null else
            MoneyFormatter.format(report.map { it.total }.reduce { a, b -> a + b }, currency)

        val monthly = if (selected == null || currency == null) emptyList() else
            monthlySeries(allTxns, CurrencyCode(selected), f.kind, f.anchor, monthsBack = 6).map { point ->
                MonthlyBar(
                    label = "%02d.%02d".format(point.month.monthValue, point.month.year % 100),
                    valueMinor = point.total.amount.raw,
                    amountText = MoneyFormatter.format(point.total, currency),
                )
            }
        val slices = if (currency == null) emptyList() else rows
            .filter { it.amountText.isNotEmpty() }
            .map { r ->
                CategorySlice(
                    name = r.name,
                    valueMinor = report.first { it.categoryId == r.categoryId }.total.amount.raw,
                    color = r.color,
                    amountText = r.amountText,
                )
            }

        // R2.3: полоса бюджета — только для расходов, только в валюте отчёта (ADR-012/I-8).
        val budgetBars: List<BudgetBar> = if (f.kind != ReportKind.EXPENSE || selected == null || currency == null) {
            emptyList()
        } else {
            val nonNullCurrency = currency
            budgetProgress(allBudgets, report, CurrencyCode(selected)).map { p ->
                BudgetBar(
                    categoryId = p.budget.categoryId,
                    spentText = MoneyFormatter.format(p.spent, nonNullCurrency),
                    budgetText = MoneyFormatter.format(p.budget.amount, nonNullCurrency),
                    percentClamped = p.percent?.coerceIn(0, 100) ?: if (p.isOverBudget) 100 else 0,
                    isOverBudget = p.isOverBudget,
                )
            }
        }
        val categoryBudgetBars = budgetBars.filter { it.categoryId != null }.associateBy { it.categoryId!! }
        val wholeCurrencyBudgetBar = budgetBars.firstOrNull { it.categoryId == null }

        val drilldown = if (f.drilldownActive && selected != null && currency != null) {
            val catName = f.drilldownCategoryId?.let { names[it] } ?: "Без категории"
            val briefs = inPeriod
                .filter { txnMatches(it, f.kind, selected, f.drilldownCategoryId) }
                .sortedByDescending { it.date }
                .map { txn ->
                    val amount = when (txn) {
                        is Txn.Expense -> txn.amount
                        is Txn.Income -> txn.amount
                        is Txn.Transfer -> txn.fromAmount
                    }
                    TxnBrief(txn.id, txn.date, MoneyFormatter.format(amount, currency), txn.note)
                }
            Drilldown(catName, briefs)
        } else {
            null
        }

        ReportUiState(
            kind = f.kind,
            periodMode = f.mode,
            periodLabel = periodLabel(f.mode, range),
            currencyCodes = currencyCodes,
            selectedCurrency = selected,
            rows = rows,
            totalText = total,
            drilldown = drilldown,
            monthly = monthly,
            slices = slices,
            categoryBudgetBars = categoryBudgetBars,
            wholeCurrencyBudgetBar = wholeCurrencyBudgetBar,
        )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    fun setKind(kind: ReportKind) = form.update { it.copy(kind = kind, drilldownActive = false) }

    fun setPeriodMode(mode: PeriodMode) = form.update { it.copy(mode = mode, drilldownActive = false) }

    fun shiftPeriod(delta: Long) = form.update {
        it.copy(anchor = shiftAnchor(it.mode, it.anchor, delta), drilldownActive = false)
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) = form.update {
        it.copy(mode = PeriodMode.CUSTOM, customStart = start, customEnd = end, drilldownActive = false)
    }

    fun selectCurrency(code: String) = form.update { it.copy(currency = code, drilldownActive = false) }

    fun openDrilldown(categoryId: String?) = form.update {
        it.copy(drilldownCategoryId = categoryId, drilldownActive = true)
    }

    fun closeDrilldown() = form.update { it.copy(drilldownActive = false) }

    companion object {
        fun factory(
            txns: TxnRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            budgets: BudgetRepository,
        ) = viewModelFactory {
            initializer { ReportViewModel(txns, categories, currencies, budgets) }
        }
    }
}

private fun fallbackCurrency(code: String): Currency =
    Currency(CurrencyCode(code), minorUnits = 2, displayScale = 2, symbol = code)

private fun txnMatches(txn: Txn, kind: ReportKind, currency: String, categoryId: String?): Boolean = when {
    kind == ReportKind.EXPENSE && txn is Txn.Expense ->
        txn.amount.currency.code == currency && txn.categoryId == categoryId
    kind == ReportKind.INCOME && txn is Txn.Income ->
        txn.amount.currency.code == currency && txn.categoryId == categoryId
    else -> false
}
