package com.corriente.data.usecase

import com.corriente.data.category.OverviewCategoriesSettings
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/** Сколько иконок категорий помещается вокруг маленького кольца. */
const val OVERVIEW_MAX_ICONS = OverviewCategoriesSettings.MAX

/** Иконка категории вокруг кольца. [percent] == null — в этом месяце по ней ничего не потрачено. */
data class OverviewIcon(
    val categoryId: String,
    val name: String,
    val icon: String?,
    val color: Int,
    val percent: Int?,
    /** Сумма по категории за месяц (для подсказки по долгому нажатию); null, если трат нет. */
    val amountText: String? = null,
)

/** Сегмент кольца; [color] == 0 у «Без категории» — экран красит его нейтральным цветом. */
data class OverviewSlice(val valueMinor: Long, val color: Int, val categoryId: String? = null)

/** Категория, вошедшая в сегмент «Прочее» (или сам сегмент); [name] == null — «Без категории». */
data class RingMember(val categoryId: String?, val name: String?, val amountText: String, val percent: Int)

/**
 * Полоска кольца. Мелкие категории (меньше [OTHER_SHARE_PERCENT]% месяца, если таких две и больше) сливаются в одну
 * полоску «Прочее» ([isOther], в [members] — всё, что в неё вошло). [name] == null у «Без категории» и у «Прочее»
 * — подписи подставляет экран. [percent] — целые проценты по всем полоскам кольца (сумма 100).
 */
data class RingSegment(
    val valueMinor: Long,
    val color: Int,
    val categoryId: String?,
    val name: String?,
    val amountText: String,
    val percent: Int,
    val isOther: Boolean = false,
    val members: List<RingMember> = emptyList(),
)

/** Категории доли меньше этой сливаются в «Прочее» на кольце. */
const val OTHER_SHARE_PERCENT = 5

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
    /** Суммы без символа валюты — экран показывает валюту отдельной строкой ([currencySymbol]). */
    val incomeAmountText: String,
    val expenseAmountText: String,
    val currencySymbol: String,
    /** Кольцо расходов (по умолчанию) и кольцо доходов — переключаются тапом по плитке «Расходы»/«Доходы». */
    val slices: List<OverviewSlice>,
    val icons: List<OverviewIcon>,
    val incomeSlices: List<OverviewSlice>,
    val incomeIcons: List<OverviewIcon>,
    /** Полоски кольца с «Прочее» — их рисует приложение; [slices]/[icons] остаются для настольной версии. */
    val segments: List<RingSegment>,
    val incomeSegments: List<RingSegment>,
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
fun buildMonthOverview(
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
    val incomeByCategory = LinkedHashMap<String?, Money>()
    monthTxns.forEach { txn ->
        when (txn) {
            is Txn.Expense -> if (inScope(txn.accountId, txn.amount)) {
                expense += txn.amount
                byCategory.merge(topLevel(txn.categoryId), txn.amount, Money::plus)
            }
            is Txn.Income -> if (inScope(txn.accountId, txn.amount)) {
                income += txn.amount
                incomeByCategory.merge(topLevel(txn.categoryId), txn.amount, Money::plus)
            }
            is Txn.Transfer -> Unit
        }
    }

    // Кольцо и иконки категорий вида [kind]; уже потраченное/полученное — в первую очередь, остальное по порядку.
    fun ring(
        byCat: Map<String?, Money>,
        total: Money,
        kind: CategoryKind,
        pins: List<String>,
    ): Pair<List<OverviewSlice>, List<OverviewIcon>> {
        val spent = byCat.entries.filter { it.value.amount.raw > 0 }.sortedByDescending { it.value.amount.raw }
        val shares = largestRemainderShares(spent.map { it.value.amount.raw }, total.amount.raw)
        val percentById = spent.mapIndexed { i, e -> e.key to shares[i] }.toMap()
        val amountById = spent.associate { it.key to MoneyFormatter.format(it.value, currency) }
        val candidates = categories.filter { it.kind == kind && it.parentId == null && !it.isArchived }
            .sortedWith(compareBy({ it.displayOrder }, { it.name }))
        val pinnedAlive = pins.filter { id -> candidates.any { it.id == id } }
        val chosen = if (pinnedAlive.isNotEmpty()) {
            pinnedAlive.take(OVERVIEW_MAX_ICONS).toSet()
        } else {
            (candidates.filter { it.id in percentById } + candidates.filterNot { it.id in percentById })
                .take(OVERVIEW_MAX_ICONS).map { it.id }.toSet()
        }
        val icons = candidates.filter { it.id in chosen }.map {
            OverviewIcon(it.id, it.name, it.icon, it.color, percentById[it.id], amountById[it.id])
        }
        val slices = spent.map { (id, money) -> OverviewSlice(money.amount.raw, id?.let { categoryById[it]?.color } ?: 0, id) }
        return slices to icons
    }
    fun segments(byCat: Map<String?, Money>, total: Money): List<RingSegment> {
        val spent = byCat.entries.filter { it.value.amount.raw > 0 }.sortedByDescending { it.value.amount.raw }
        val grand = total.amount.raw
        fun small(e: Map.Entry<String?, Money>) = Math.multiplyExact(e.value.amount.raw, 100L) < grand * OTHER_SHARE_PERCENT
        val smalls = spent.filter(::small)
        val merge = smalls.size >= 2
        val big = if (merge) spent.filterNot(::small) else spent
        val values = big.map { it.value.amount.raw } + if (merge) listOf(smalls.sumOf { it.value.amount.raw }) else emptyList()
        val shares = largestRemainderShares(values, grand)
        val memberShares = if (merge) largestRemainderShares(smalls.map { it.value.amount.raw }, grand) else emptyList()
        val result = big.mapIndexed { i, (id, money) ->
            RingSegment(
                valueMinor = money.amount.raw,
                color = id?.let { categoryById[it]?.color } ?: 0,
                categoryId = id,
                name = id?.let { categoryById[it]?.name },
                amountText = MoneyFormatter.format(money, currency),
                percent = shares[i],
            )
        }
        if (!merge) return result
        val otherMoney = smalls.fold(Money.zero(code)) { acc, e -> acc + e.value }
        return result + RingSegment(
            valueMinor = otherMoney.amount.raw,
            color = 0,
            categoryId = null,
            name = null,
            amountText = MoneyFormatter.format(otherMoney, currency),
            percent = shares.last(),
            isOther = true,
            members = smalls.mapIndexed { i, (id, money) ->
                RingMember(id, id?.let { categoryById[it]?.name }, MoneyFormatter.format(money, currency), memberShares[i])
            },
        )
    }
    val (expenseSlices, expenseIcons) = ring(byCategory, expense, CategoryKind.EXPENSE, pinned)
    val (incomeSlices, incomeIcons) = ring(incomeByCategory, income, CategoryKind.INCOME, emptyList())

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
        incomeAmountText = MoneyFormatter.format(income, currency).removeSuffix("${MoneyFormatter.NBSP}${currency.symbol}"),
        expenseAmountText = MoneyFormatter.format(expense, currency).removeSuffix("${MoneyFormatter.NBSP}${currency.symbol}"),
        currencySymbol = currency.symbol,
        slices = expenseSlices,
        icons = expenseIcons,
        incomeSlices = incomeSlices,
        incomeIcons = incomeIcons,
        segments = segments(byCategory, expense),
        incomeSegments = segments(incomeByCategory, income),
        currencyOptions = currencyOptions,
        accountOptions = active,
    )
}

