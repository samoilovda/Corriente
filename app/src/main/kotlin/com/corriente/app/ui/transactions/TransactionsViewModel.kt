package com.corriente.app.ui.transactions

import com.corriente.app.ui.common.WritingViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** F2.1: сколько месяцев истории показываем по умолчанию и на сколько расширяем по «показать ещё». */
private const val DEFAULT_WINDOW_MONTHS = 3L

/**
 * Фильтры списка операций (T5.2). [query] ищет по заметке и названию категории без учёта
 * регистра. [amount] (U01) сравнивает модуль суммы в минорных единицах только у сторон операции
 * в валюте порога — это фильтр, не арифметика: сложения и конвертации валют тут нет (I-8).
 */
data class TxnFilter(
    val accountId: String? = null,
    val currencyCode: String? = null,
    val query: String = "",
    val categoryId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val amount: AmountRange? = null,
) {
    val isActive: Boolean
        get() = accountId != null || currencyCode != null || query.isNotBlank() || categoryId != null ||
            from != null || to != null || amount != null
}

data class TxnRow(
    val id: String,
    val title: String,
    val note: String?,
    val amountText: String,
    /** true — расход/доход, открывается в экране ввода. */
    val editable: Boolean,
    /** true — перевод, открывается в экране перевода. */
    val isTransfer: Boolean = false,
    /** Эмодзи-иконка категории (T5.5), null у переводов и операций без категории. */
    val icon: String? = null,
    /** Цвет категории (ARGB); 0 — нет цвета. */
    val color: Int = 0,
    /** R3.0: цвет счёта (ARGB), с которого/на который прошла операция; 0 — нет цвета. */
    val accountColor: Int = 0,
    /** R3.3: название счёта — для CSV-выгрузки списка операций (у перевода — «откуда → куда»). */
    val accountName: String = "",
    /** A3.15: сумма числом со знаком для CSV («-1234.56»); у перевода — сторона-источник. */
    val amountPlain: String = "",
    /** A3.15: код валюты [amountPlain]. */
    val currencyCode: String = "",
    /** UI-1.3: тип суммы — от него зависит цвет в списке. */
    val kind: com.corriente.app.ui.common.MoneyKind = com.corriente.app.ui.common.MoneyKind.EXPENSE,
)

/** Итог дня считается по каждой валюте отдельно (I-8) — сложение разных валют невозможно. */
data class DaySection(
    val date: LocalDate,
    val totals: List<String>,
    val rows: List<TxnRow>,
)

data class TransactionsUiState(
    val sections: List<DaySection> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencyCodes: List<String> = emptyList(),
    /** U01: справочник валют — точность (`minorUnits`) порогов суммы и их показ. */
    val currencies: Map<String, Currency> = emptyMap(),
    /** U01: валюта каждого счёта (и архивного) — валютный контекст порога суммы. */
    val accountCurrencies: Map<String, String> = emptyMap(),
    val filter: TxnFilter = TxnFilter(),
    val empty: Boolean = true,
    /** true — есть операции, но под активный фильтр ничего не подошло. */
    val noMatch: Boolean = false,
    /** F2.1: можно подгрузить более старые операции — окно не покрывает всю историю. */
    val canLoadEarlier: Boolean = false,
    /** U11: в базе нет ни одного счёта (и архивного) — новый пользователь; `null`, пока не загружено. */
    val noAccountsAtAll: Boolean? = null,
)

private fun matchesAccount(txn: Txn, accountId: String): Boolean = when (txn) {
    is Txn.Expense -> txn.accountId == accountId
    is Txn.Income -> txn.accountId == accountId
    is Txn.Transfer -> txn.fromAccountId == accountId || txn.toAccountId == accountId
}

private fun currenciesOf(txn: Txn): List<String> = when (txn) {
    is Txn.Expense -> listOf(txn.amount.currency.code)
    is Txn.Income -> listOf(txn.amount.currency.code)
    is Txn.Transfer -> listOf(txn.fromAmount.currency.code, txn.toAmount.currency.code)
}

private fun matchesCurrency(txn: Txn, code: String): Boolean = code in currenciesOf(txn)

private fun categoryIdOf(txn: Txn): String? = when (txn) {
    is Txn.Expense -> txn.categoryId
    is Txn.Income -> txn.categoryId
    is Txn.Transfer -> null
}

private fun amountSidesOf(txn: Txn): List<Money> = when (txn) {
    is Txn.Expense -> listOf(txn.amount)
    is Txn.Income -> listOf(txn.amount)
    is Txn.Transfer -> listOf(txn.fromAmount, txn.toAmount)
}

/** U01: обе границы проверяются на одной и той же стороне операции и только в валюте порога. */
private fun matchesAmount(txn: Txn, range: AmountRange): Boolean = amountSidesOf(txn).any { side ->
    side.currency.code == range.currencyCode &&
        (range.minMinor == null || side.amount.raw >= range.minMinor) &&
        (range.maxMinor == null || side.amount.raw <= range.maxMinor)
}

private fun accountIdsOf(txn: Txn): List<String> = when (txn) {
    is Txn.Expense -> listOf(txn.accountId)
    is Txn.Income -> listOf(txn.accountId)
    is Txn.Transfer -> listOf(txn.fromAccountId, txn.toAccountId)
}

/**
 * R2.1: заметка, название категории и название счёта — та же область поиска, что и в
 * [com.corriente.data.repository.TxnRepository.search] (FTS/LIKE), только над уже загруженным
 * в память списком: этот путь используется для остальных полей фильтра (счёт/период/сумма),
 * когда поисковый запрос уже отфильтровал операции через БД (см. [TransactionsViewModel]).
 */
private fun matchesQuery(
    txn: Txn,
    query: String,
    categoryNames: Map<String, String>,
    accountNames: Map<String, String>,
): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    val haystack = buildList {
        txn.note?.let { add(it) }
        categoryIdOf(txn)?.let { categoryNames[it] }?.let { add(it) }
        accountIdsOf(txn).forEach { accountNames[it]?.let(::add) }
    }
    return haystack.any { it.lowercase().contains(needle) }
}

private fun matchesFilter(
    txn: Txn,
    filter: TxnFilter,
    categoryNames: Map<String, String>,
    accountNames: Map<String, String> = emptyMap(),
    /** true — запрос уже применён на уровне БД ([TransactionsViewModel.rangeTxns]), здесь его не проверяем повторно. */
    querySatisfiedExternally: Boolean = false,
): Boolean {
    if (filter.accountId != null && !matchesAccount(txn, filter.accountId)) return false
    if (filter.currencyCode != null && !matchesCurrency(txn, filter.currencyCode)) return false
    if (filter.categoryId != null && categoryIdOf(txn) != filter.categoryId) return false
    if (filter.from != null && txn.date < filter.from) return false
    if (filter.to != null && txn.date > filter.to) return false
    if (!querySatisfiedExternally && !matchesQuery(txn, filter.query, categoryNames, accountNames)) return false
    if (filter.amount != null && !matchesAmount(txn, filter.amount)) return false
    return true
}

private fun currencyOrFallback(code: CurrencyCode, byCode: Map<String, Currency>): Currency =
    byCode[code.code] ?: Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

/**
 * Плоский список операций → секции по дням (свежие сверху), с итогом по каждой валюте.
 * Переводы показываются строкой, но в итог дня не входят (I-11).
 */
internal fun buildDaySections(
    txns: List<Txn>,
    filter: TxnFilter,
    accountNames: Map<String, String>,
    categoryNames: Map<String, String>,
    currenciesByCode: Map<String, Currency>,
    categoryIcons: Map<String, String?> = emptyMap(),
    categoryColors: Map<String, Int> = emptyMap(),
    /** R3.0: цвет счёта (ARGB), 0/отсутствие — «нет цвета». */
    accountColors: Map<String, Int> = emptyMap(),
    /** R2.1: true, когда [txns] уже пришли из [com.corriente.data.repository.TxnRepository.search]. */
    querySatisfiedExternally: Boolean = false,
): List<DaySection> {
    val filtered = txns.filter {
        matchesFilter(it, filter, categoryNames, accountNames, querySatisfiedExternally)
    }

    return filtered.groupBy { it.date }.entries
        .sortedByDescending { it.key }
        .map { (date, dayTxns) ->
            DaySection(
                date = date,
                totals = dayTotals(dayTxns, currenciesByCode),
                rows = dayTxns.map { txn ->
                    row(txn, accountNames, categoryNames, currenciesByCode, categoryIcons, categoryColors, accountColors)
                },
            )
        }
}

private fun dayTotals(dayTxns: List<Txn>, byCode: Map<String, Currency>): List<String> {
    // I-3: накапливаем через Money.plus/minus (Math.*Exact) — унарный минус и сложение над
    // голым Long обходили бы проверку переполнения (F1.1).
    val nets = mutableMapOf<CurrencyCode, Money>()
    dayTxns.forEach { txn ->
        when (txn) {
            is Txn.Expense -> nets.merge(txn.amount.currency, -txn.amount, Money::plus)
            is Txn.Income -> nets.merge(txn.amount.currency, txn.amount, Money::plus)
            is Txn.Transfer -> Unit
        }
    }
    return nets.entries.sortedBy { it.key.code }.map { (code, net) ->
        MoneyFormatter.format(net, currencyOrFallback(code, byCode))
    }
}

private fun row(
    txn: Txn,
    accountNames: Map<String, String>,
    categoryNames: Map<String, String>,
    byCode: Map<String, Currency>,
    categoryIcons: Map<String, String?>,
    categoryColors: Map<String, Int>,
    accountColors: Map<String, Int> = emptyMap(),
): TxnRow = when (txn) {
    is Txn.Expense -> {
        val currency = currencyOrFallback(txn.amount.currency, byCode)
        TxnRow(
            id = txn.id,
            title = txn.categoryId?.let { categoryNames[it] } ?: "—",
            note = txn.note,
            amountText = MoneyFormatter.format(-txn.amount, currency),
            amountPlain = MoneyFormatter.formatPlain(-txn.amount, currency),
            currencyCode = currency.code.code,
            editable = true,
            icon = txn.categoryId?.let { categoryIcons[it] },
            color = txn.categoryId?.let { categoryColors[it] } ?: 0,
            accountColor = accountColors[txn.accountId] ?: 0,
            accountName = accountNames[txn.accountId] ?: "",
        )
    }
    is Txn.Income -> {
        val currency = currencyOrFallback(txn.amount.currency, byCode)
        TxnRow(
            id = txn.id,
            title = txn.categoryId?.let { categoryNames[it] } ?: "—",
            note = txn.note,
            amountText = "+" + MoneyFormatter.format(txn.amount, currency),
            kind = com.corriente.app.ui.common.MoneyKind.INCOME,
            amountPlain = MoneyFormatter.formatPlain(txn.amount, currency),
            currencyCode = currency.code.code,
            editable = true,
            icon = txn.categoryId?.let { categoryIcons[it] },
            color = txn.categoryId?.let { categoryColors[it] } ?: 0,
            accountColor = accountColors[txn.accountId] ?: 0,
            accountName = accountNames[txn.accountId] ?: "",
        )
    }
    is Txn.Transfer -> {
        val from = currencyOrFallback(txn.fromAmount.currency, byCode)
        val to = currencyOrFallback(txn.toAmount.currency, byCode)
        val fromName = accountNames[txn.fromAccountId] ?: "?"
        val toName = accountNames[txn.toAccountId] ?: "?"
        TxnRow(
            id = txn.id,
            title = "$fromName → $toName",
            kind = com.corriente.app.ui.common.MoneyKind.TRANSFER,
            note = txn.note,
            amountText = "${MoneyFormatter.format(txn.fromAmount, from)} → ${MoneyFormatter.format(txn.toAmount, to)}",
            amountPlain = MoneyFormatter.formatPlain(-txn.fromAmount, from),
            currencyCode = from.code.code,
            editable = false,
            isTransfer = true,
            // R3.0: у перевода два счёта — маркер красим цветом счёта-источника.
            accountColor = accountColors[txn.fromAccountId] ?: 0,
            accountName = "$fromName → $toName",
        )
    }
}

class TransactionsViewModel(
    private val txns: TxnRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    currencies: CurrencyRepository,
    io: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.IO,
    private val csvHeaders: com.corriente.data.export.CsvHeaders = com.corriente.data.export.CsvHeaders.ENGLISH,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel(io) {

    private val filter = MutableStateFlow(TxnFilter())

    /** UI-3.2: только что удалённая свайпом операция — копия для «Отменить» (I-22: из БД она уже ушла). */
    private val _pendingUndo = MutableStateFlow<Txn?>(null)
    val pendingUndo: kotlinx.coroutines.flow.StateFlow<Txn?> = _pendingUndo

    /** Удаление свайпом: физическое и немедленное, копия живёт здесь, пока снекбар не истёк. */
    fun deleteTxn(id: String) {
        launchWrite(onError = { com.corriente.app.ui.common.uiMessage(com.corriente.app.R.string.txn_entry_error_delete) }) {
            val existing = txns.getById(id) ?: return@launchWrite
            txns.deleteById(id)
            _pendingUndo.value = existing
        }
    }

    /** Отмена удаления — та же операция, тот же `id`/`createdAt`/`import_hash` (как в R5.3). */
    fun undoDelete() {
        val txn = _pendingUndo.value ?: return
        _pendingUndo.value = null
        launchWrite(onError = { com.corriente.app.ui.common.uiMessage(com.corriente.app.R.string.txn_entry_error_restore) }) {
            txns.restore(txn)
        }
    }

    /** Снекбар истёк без отмены — удаление окончательно. */
    fun consumeUndo() {
        _pendingUndo.value = null
    }

    /** F2.1: сколько месяцев истории подгружено. Растёт по «показать ещё». */
    private val windowMonths = MutableStateFlow(DEFAULT_WINDOW_MONTHS)

    /** Начало окна с учётом фильтра по дате (если он уходит глубже — грузим глубже). */
    private fun windowStart(months: Long, f: TxnFilter): LocalDate {
        val base = today().minusMonths(months)
        return f.from?.let { minOf(it, base) } ?: base
    }

    /** [windowStart] — null, когда источник — [TxnRepository.search] (вся история, окно не применимо). */
    private data class TxnSource(
        val windowStart: LocalDate?,
        val txns: List<Txn>,
        val isSearch: Boolean,
        /** A1.1: за границей окна есть ещё операции. */
        val anyBefore: Boolean = false,
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val rangeTxns: kotlinx.coroutines.flow.Flow<TxnSource> =
        combine(windowMonths, filter) { m, f -> f.query.isNotBlank() to windowStart(m, f) }
            .distinctUntilChanged()
            .flatMapLatest { (isSearchNow, start) ->
                if (isSearchNow) {
                    // R2.1: поиск уходит в БД по всей истории, а не по подгруженному окну месяцев —
                    // именно это даёт «на 50 000 операций без заметной задержки» (критерий приёмки).
                    filter.map { it.query }.distinctUntilChanged()
                        .flatMapLatest { q -> txns.search(q).map { TxnSource(null, it, isSearch = true) } }
                } else {
                    // A2.7: верхней границы нет — операции с будущей датой (уже учтённые в балансе счёта) видны сверху списка.
                    combine(txns.observeRange(start, OPEN_END), txns.observeAnyBefore(start)) { range, before ->
                        TxnSource(start, range, isSearch = false, anyBefore = before)
                    }
                }
            }

    val uiState: StateFlow<TransactionsUiState> = combine(
        rangeTxns,
        accounts.observeAll(),
        categories.observeAllForLookup(),
        currencies.observeAll(),
        combine(filter, txns.observeAnyExist()) { f, any -> f to any },
    ) { source, allAccounts, allCategories, allCurrencies, filterAndAny ->
        val (windowStart, windowTxns, isSearch, anyBefore) = source
        val (currentFilter, anyExist) = filterAndAny
        val byCode = allCurrencies.associateBy { it.code.code }
        val accountCurrencies = allAccounts.associate { it.id to it.currency.code }
        val accountId = currentFilter.accountId?.takeIf { id -> allAccounts.any { it.id == id } }
        val sanitizedFilter = currentFilter.copy(
            accountId = accountId,
            currencyCode = currentFilter.currencyCode?.takeIf { code -> byCode.containsKey(code) },
            categoryId = currentFilter.categoryId?.takeIf { id -> allCategories.any { it.id == id } },
            // U01: порог в валюте, которой нет в справочнике или которая расходится с валютой выбранного счёта, не применяется.
            amount = currentFilter.amount?.takeIf { range ->
                byCode.containsKey(range.currencyCode) &&
                    (accountId == null || accountCurrencies[accountId] == range.currencyCode)
            },
        )
        val sections = buildDaySections(
            txns = windowTxns,
            filter = sanitizedFilter,
            querySatisfiedExternally = isSearch,
            accountNames = allAccounts.associate { it.id to it.name },
            categoryNames = allCategories.associate { it.id to it.name },
            currenciesByCode = byCode,
            categoryIcons = allCategories.associate { it.id to it.icon },
            categoryColors = allCategories.associate { it.id to it.color },
            accountColors = allAccounts.associate { it.id to it.color },
        )
        // A1.1: «Показать ещё» — только если за границей окна есть операции (запрос в БД). Поиск
        // (R2.1) уже смотрит во всю историю — «показать ещё» ему не нужно.
        val canLoadEarlier = !isSearch && windowStart != null && anyBefore && sanitizedFilter.from == null
        TransactionsUiState(
            sections = sections,
            accounts = allAccounts.filterNot { it.isArchived },
            categories = allCategories.filterNot { it.isArchived },
            currencyCodes = allAccounts.map { it.currency.code }.distinct().sorted(),
            currencies = byCode,
            accountCurrencies = accountCurrencies,
            filter = sanitizedFilter,
            empty = !anyExist,
            noMatch = anyExist && sections.isEmpty(),
            canLoadEarlier = canLoadEarlier,
            noAccountsAtAll = allAccounts.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun loadEarlier() = windowMonths.update { it + DEFAULT_WINDOW_MONTHS }

    /**
     * Кольцо над списком может уйти на месяц глубже окна — тогда расширяем окно (шагами по
     * [DEFAULT_WINDOW_MONTHS], как «показать ещё»), чтобы операции этого месяца были в списке.
     */
    fun ensureWindowCovers(month: java.time.YearMonth) {
        val needed = java.time.temporal.ChronoUnit.MONTHS.between(month.atDay(1), today()) + 1
        windowMonths.update { current ->
            if (needed <= current) {
                current
            } else {
                current + (needed - current + DEFAULT_WINDOW_MONTHS - 1) / DEFAULT_WINDOW_MONTHS * DEFAULT_WINDOW_MONTHS
            }
        }
    }

    /** U01: валюты счетов для сброса порога суммы при смене валютного контекста вне листа фильтров. */
    private val knownAccounts: StateFlow<List<Account>> =
        accounts.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setAccountFilter(accountId: String?) = changeCurrencyContext { it.copy(accountId = accountId) }

    fun setCurrencyFilter(code: String?) = changeCurrencyContext { it.copy(currencyCode = code) }

    /**
     * Счёт или валюта сменились (напр. выбор счёта в шапке): порог суммы в прежней валюте не
     * переинтерпретируется в новой — сбрасывается с сообщением. Снятие счёта/валюты порог сохраняет.
     */
    private fun changeCurrencyContext(change: (TxnFilter) -> TxnFilter) {
        val accountCurrencies = knownAccounts.value.associate { it.id to it.currency.code }
        var reset = false
        filter.update { current ->
            val next = change(current)
            val context = effectiveCurrency(next.accountId, next.currencyCode, accountCurrencies)
            reset = next.amount != null && context != null && context != next.amount.currencyCode
            if (reset) next.copy(amount = null) else next
        }
        if (reset) postMessage(com.corriente.app.R.string.txn_filter_amount_reset)
    }

    /** U01/U04: лист фильтров применяется целиком — уже проверенный [TxnFilterDraft.validate] фильтр. */
    fun applyFilter(newFilter: TxnFilter) {
        filter.value = newFilter
    }

    fun clearAmountFilter() = filter.update { it.copy(amount = null) }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setCategoryFilter(categoryId: String?) = filter.update { it.copy(categoryId = categoryId) }

    fun setPeriod(from: LocalDate?, to: LocalDate?) = filter.update { it.copy(from = from, to = to) }

    fun clearFilters() = filter.update { TxnFilter() }

    /**
     * R3.3: CSV списка операций с уже применёнными на экране фильтрами — ровно то, что видно
     * в [uiState] сейчас (те же секции по дням, тот же порядок), суммы уже отформатированы
     * [com.corriente.money.MoneyFormatter] (I-25).
     */
    /** A2.1: CSV списка в файл, выбранный через SAF ([open] вызывается на IO). */
    fun exportCsvTo(open: () -> java.io.OutputStream) = writeFile(open) { com.corriente.data.export.CsvExport.encodeForFile(exportCsv()) }

    fun exportCsv(): String {
        val rows = uiState.value.sections.flatMap { section ->
            section.rows.map { row ->
                com.corriente.data.export.TxnCsvRow(
                    date = section.date.toString(),
                    account = row.accountName,
                    category = row.title,
                    note = row.note ?: "",
                    amountText = row.amountText,
                    amountValue = row.amountPlain,
                    currency = row.currencyCode,
                )
            }
        }
        return com.corriente.data.export.CsvExport.txnCsv(rows, csvHeaders)
    }

    companion object {
        /** «Без верхней границы» для `observeRange`: даты в БД — ISO-строки, сравнение лексикографическое. */
        private val OPEN_END: LocalDate = LocalDate.of(9999, 12, 31)

        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            csvHeaders: com.corriente.data.export.CsvHeaders = com.corriente.data.export.CsvHeaders.ENGLISH,
        ) = viewModelFactory {
            initializer { TransactionsViewModel(txns, accounts, categories, currencies, csvHeaders = csvHeaders) }
        }
    }
}
