package com.corriente.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.report.largestRemainderShares
import com.corriente.data.category.OverviewCategoriesSettings
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.AccountBalance
import com.corriente.data.usecase.AccountBalanceUseCase
import com.corriente.data.usecase.totalsByCurrency
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

/** Сколько иконок категорий помещается вокруг маленького кольца. */
internal const val OVERVIEW_MAX_ICONS = OverviewCategoriesSettings.MAX

/** Иконка категории вокруг кольца. [percent] == null — в этом месяце по ней ничего не потрачено. */
data class OverviewIcon(
    val categoryId: String,
    val name: String,
    val icon: String?,
    val color: Int,
    val percent: Int?,
)

/** Сегмент кольца; [color] == 0 у «Без категории» — экран красит его нейтральным цветом. */
data class OverviewSlice(val valueMinor: Long, val color: Int)

/**
 * Шапка «Операций» в духе Monefy: расходы месяца кольцом по категориям, доход/расход в центре, баланс под кольцом.
 * Всё — в одной валюте (I-8): либо выбранного счёта, либо выбранной валюты по всем счетам.
 */
data class MonthOverview(
    val month: YearMonth,
    /** null — «Все счета» в валюте [currencyCode]. */
    val accountId: String?,
    val accountName: String?,
    val currencyCode: String,
    val incomeText: String,
    val expenseText: String,
    val balanceText: String,
    val balanceNegative: Boolean,
    val slices: List<OverviewSlice>,
    val icons: List<OverviewIcon>,
    /** Варианты выбора в шапке: валюты счетов (для «Все счета») и сами счета. */
    val currencyOptions: List<String>,
    val accountOptions: List<Account>,
)

private fun currencyOf(code: CurrencyCode, byCode: Map<String, Currency>): Currency =
    byCode[code.code] ?: Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

/**
 * Чистая сборка [MonthOverview] — тестируется без БД. [monthTxns] — операции месяца [month]; подкатегории
 * сворачиваются в родителя (у кольца одна степень детализации, как в Monefy).
 * @return null, если нет ни одного активного счёта — показывать нечего.
 */
internal fun buildMonthOverview(
    month: YearMonth,
    monthTxns: List<Txn>,
    accounts: List<Account>,
    categories: List<Category>,
    currencies: List<Currency>,
    balances: List<AccountBalance>,
    selectedAccountId: String?,
    selectedCurrency: String?,
    /** Выбранные пользователем категории вокруг кольца; пусто — автоматически. */
    pinned: List<String> = emptyList(),
): MonthOverview? {
    val active = accounts.filterNot { it.isArchived }
    val account = selectedAccountId?.let { id -> active.firstOrNull { it.id == id } }
    val currencyOptions = active.map { it.currency.code }.distinct().sorted()
    val code = account?.currency
        ?: selectedCurrency?.takeIf { it in currencyOptions }?.let(::CurrencyCode)
        ?: active.firstOrNull()?.currency
        ?: return null
    val byCode = currencies.associateBy { it.code.code }
    val currency = currencyOf(code, byCode)
    val categoryById = categories.associateBy { it.id }
    fun topLevel(id: String?): String? = id?.let { categoryById[it]?.parentId ?: it }

    fun inScope(accountId: String, money: Money) =
        if (account != null) accountId == account.id else money.currency == code

    // I-3: суммы — только через Money.plus.
    var income = Money.zero(code)
    var expense = Money.zero(code)
    val byCategory = LinkedHashMap<String?, Money>()
    monthTxns.forEach { txn ->
        when (txn) {
            is Txn.Expense -> if (inScope(txn.accountId, txn.amount)) {
                expense += txn.amount
                byCategory.merge(topLevel(txn.categoryId), txn.amount, Money::plus)
            }
            is Txn.Income -> if (inScope(txn.accountId, txn.amount)) income += txn.amount
            is Txn.Transfer -> Unit
        }
    }

    val spent = byCategory.entries.filter { it.value.amount.raw > 0 }.sortedByDescending { it.value.amount.raw }
    val shares = largestRemainderShares(spent.map { it.value.amount.raw }, expense.amount.raw)
    val percentById = spent.mapIndexed { i, e -> e.key to shares[i] }.toMap()

    // Уже потраченное — в первую очередь, остальное добирается по порядку категорий; на экране — порядок категорий.
    val candidates = categories.filter { it.kind == CategoryKind.EXPENSE && it.parentId == null && !it.isArchived }
        .sortedWith(compareBy({ it.displayOrder }, { it.name }))
    val pinnedAlive = pinned.filter { id -> candidates.any { it.id == id } }
    val chosen = if (pinnedAlive.isNotEmpty()) {
        pinnedAlive.take(OVERVIEW_MAX_ICONS).toSet()
    } else {
        (candidates.filter { it.id in percentById } + candidates.filterNot { it.id in percentById })
            .take(OVERVIEW_MAX_ICONS).map { it.id }.toSet()
    }
    val icons = candidates.filter { it.id in chosen }.map {
        OverviewIcon(it.id, it.name, it.icon, it.color, percentById[it.id])
    }

    val balance = if (account != null) {
        balances.firstOrNull { it.account.id == account.id }?.balance ?: account.openingBalance
    } else {
        totalsByCurrency(balances)[code] ?: Money.zero(code)
    }

    return MonthOverview(
        month = month,
        accountId = account?.id,
        accountName = account?.name,
        currencyCode = code.code,
        incomeText = MoneyFormatter.format(income, currency),
        expenseText = MoneyFormatter.format(expense, currency),
        balanceText = MoneyFormatter.format(balance, currency),
        balanceNegative = balance.amount.raw < 0,
        slices = spent.map { (id, money) -> OverviewSlice(money.amount.raw, id?.let { categoryById[it]?.color } ?: 0) },
        icons = icons,
        currencyOptions = currencyOptions,
        accountOptions = active,
    )
}

/** Состояние шапки; [overview] == null — нет счетов (шапку не показываем). */
data class MonthOverviewUiState(val overview: MonthOverview? = null)

/**
 * Данные шапки «Операций». Выбор счёта общий со списком (его задаёт экран через [setAccount] и фильтр
 * списка); валюта для «Все счета» и месяц — только для шапки, список они не фильтруют.
 */
class MonthOverviewViewModel(
    txns: TxnRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    currencies: CurrencyRepository,
    balances: AccountBalanceUseCase,
    overviewSettings: OverviewCategoriesSettings? = null,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val accountId = MutableStateFlow<String?>(null)
    private val currency = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTxns = month.flatMapLatest { m -> txns.observeRange(m.atDay(1), m.atEndOfMonth()) }

    val uiState: StateFlow<MonthOverviewUiState> = combine(
        combine(month, monthTxns) { m, list -> m to list },
        accounts.observeAll(),
        categories.observeAllForLookup(),
        combine(currencies.observeAll(), balances.observeBalances()) { c, b -> c to b },
        combine(accountId, currency, overviewSettings?.pinned ?: flowOf(emptyList())) { a, c, p -> Triple(a, c, p) },
    ) { (m, list), allAccounts, allCategories, (allCurrencies, allBalances), (selAccount, selCurrency, pins) ->
        MonthOverviewUiState(
            buildMonthOverview(m, list, allAccounts, allCategories, allCurrencies, allBalances, selAccount, selCurrency, pins),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthOverviewUiState())

    fun shiftMonth(delta: Long) = month.update { it.plusMonths(delta) }

    fun setAccount(id: String?) {
        accountId.value = id
    }

    /** «Все счета» в валюте [code]. */
    fun setAllAccounts(code: String) {
        accountId.value = null
        currency.value = code
    }

    companion object {
        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            balances: AccountBalanceUseCase,
            overviewSettings: OverviewCategoriesSettings? = null,
        ) = viewModelFactory {
            initializer { MonthOverviewViewModel(txns, accounts, categories, currencies, balances, overviewSettings) }
        }
    }
}
