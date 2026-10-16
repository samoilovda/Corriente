package com.corriente.app.ui.txnentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.R
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.uiMessage
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.quickentry.FREQUENT_ENTRIES_WINDOW_DAYS
import com.corriente.data.quickentry.FREQUENT_ENTRY_SUGGESTIONS_LIMIT
import com.corriente.data.quickentry.FrequentEntry
import com.corriente.data.quickentry.FrequentEntryKind
import com.corriente.data.quickentry.frequentEntries
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.AmountInput
import com.corriente.money.CalcOp
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import com.corriente.money.applyCalcOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Расход или доход. Перевод — отдельный поток ввода (T2.1). */
enum class EntryKind { EXPENSE, INCOME }

/** Счёт как вариант выбора в форме — с уже разрешённой [Currency] для клавиатуры и показа. */
data class AccountOption(val id: String, val name: String, val currency: Currency, val isArchived: Boolean = false)

/**
 * R2.2: шаблон быстрого ввода для строки «частые» — с уже разрешёнными для показа именем
 * категории/счёта и отформатированной суммой (I-1: деньги в UI-state — строка).
 */
data class FrequentOption(
    val entry: FrequentEntry,
    val label: String,
    val amountText: String,
)

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
    /** R2.2: строка «частые» над клавиатурой — пусто при редактировании существующей операции. */
    val frequentOptions: List<FrequentOption> = emptyList(),
    /** Запись в полёте — экран обязан отключить «Сохранить» на это время (см. [Form.saving]). */
    val saving: Boolean = false,
    /** A0.2: последнее нажатие «+ − × ÷ =» дало результат вне допустимого диапазона — форма не менялась. */
    val calcOverflow: Boolean = false,
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
        return if (calcAcc != null && calcOp != null) {
            // A0.2: переполнение — не исключение (resolvedMinor зовут combine и композиция), а «нет суммы».
            applyCalcOrNull(Minor(calcAcc), calcOp, operand, currency.minorUnits)
        } else {
            operand
        }
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
    /** Тап по категории на главном экране: ввод сразу с этой категорией (и счётом, если на главном выбран счёт). */
    private val initialCategoryId: String? = null,
    private val initialAccountId: String? = null,
    /** UI-3.2: «Повторить» — форма заполняется как у этой операции, дата — сегодня (сама операция не меняется). */
    private val repeatTxnId: String? = null,
    /**
     * R5.4: форма живёт здесь, а не только в [form] — `SavedStateHandle` переживает убийство
     * процесса («Не сохранять действия» в параметрах разработчика), обычный `MutableStateFlow`
     * внутри ViewModel нет. Часть уже подключённого `lifecycle-viewmodel` (ROADMAP.md §7,
     * R5.4) — новой зависимости не требует.
     */
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
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
        /** Запись уже в полёте — [save] обязан игнорировать повторный вызов, пока она не завершится
         *  (двойной тап до навигации прочь с экрана иначе создавал вторую операцию). Не зеркалится
         *  в [SavedStateHandle] — переживший процесс перезапуск обязан начинать не-«в записи». */
        val saving: Boolean = false,
        /** A0.2: сообщение «Слишком большое число»; сбрасывается любым следующим вводом. */
        val calcOverflow: Boolean = false,
    )

    /** Ключи в [savedStateHandle] — префикс на случай, если когда-нибудь появится общий Bundle. */
    private object SavedKeys {
        const val KIND = "txn_entry.kind"
        const val AMOUNT_TEXT = "txn_entry.amount_text"
        const val CALC_ACC = "txn_entry.calc_acc"
        const val CALC_OP = "txn_entry.calc_op"
        const val ACCOUNT_ID = "txn_entry.account_id"
        const val CATEGORY_ID = "txn_entry.category_id"
        const val DATE = "txn_entry.date"
        const val NOTE = "txn_entry.note"
    }

    private fun persistForm(f: Form) {
        savedStateHandle[SavedKeys.KIND] = f.kind.name
        savedStateHandle[SavedKeys.AMOUNT_TEXT] = f.amount.displayText()
        savedStateHandle[SavedKeys.CALC_ACC] = f.calcAcc
        savedStateHandle[SavedKeys.CALC_OP] = f.calcOp?.name
        savedStateHandle[SavedKeys.ACCOUNT_ID] = f.selectedAccountId
        savedStateHandle[SavedKeys.CATEGORY_ID] = f.selectedCategoryId
        savedStateHandle[SavedKeys.DATE] = f.date.toString()
        savedStateHandle[SavedKeys.NOTE] = f.note
    }

    private val form = MutableStateFlow(Form(kind = initialKind, date = today()))

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    /**
     * R5.3: операция, только что удалённая с этого экрана и ещё не подтверждённая
     * бесповоротно — снекбар держит её в памяти на [UNDO_WINDOW_MS], пока не истечёт таймер
     * или пользователь не нажмёт «Отменить». Удаление из БД уже произошло (I-22: физическое,
     * без флагов) — это только копия для возможной отмены.
     */
    private val _pendingUndo = MutableStateFlow<Txn?>(null)
    val pendingUndo: StateFlow<Txn?> = _pendingUndo

    private var undoJob: Job? = null

    val isEditing: Boolean get() = editingTxnId != null

    init {
        viewModelScope.launch {
            // R5.4: сперва восстанавливаем форму — из SavedStateHandle предыдущей жизни
            // процесса (важнее свежей загрузки из БД: непроверенные правки не теряются) либо
            // из БД при редактировании — и только ПОТОМ включаем зеркалирование формы в
            // SavedStateHandle. Иначе первая (пустая) эмиссия `form` затирала бы сохранённое
            // состояние раньше, чем `restoreFormFromSavedState` успевает его прочитать.
            when {
                savedStateHandle.get<String>(SavedKeys.KIND) != null -> {
                    form.value = restoreFormFromSavedState()
                }
                repeatTxnId != null && editingTxnId == null -> {
                    val source = txns.getById(repeatTxnId)
                    val prefill = when (source) {
                        is Txn.Expense -> Quad(EntryKind.EXPENSE, source.accountId, source.amount, source.categoryId)
                        is Txn.Income -> Quad(EntryKind.INCOME, source.accountId, source.amount, source.categoryId)
                        is Txn.Transfer, null -> null // перевод так не повторяется
                    }
                    if (source != null && prefill != null) {
                        val (kind, accountId, amount, categoryId) = prefill
                        val currency = currencies.getByCode(amount.currency)
                            ?: Currency(amount.currency, minorUnits = 2, displayScale = 2, symbol = amount.currency.code)
                        form.value = Form(
                            kind = kind,
                            amount = AmountInput.fromMinor(amount.amount, currency),
                            selectedAccountId = accountId,
                            selectedCategoryId = categoryId,
                            date = today(),
                            note = source.note.orEmpty(),
                        )
                    }
                }
                editingTxnId != null -> {
                    val existing = txns.getById(editingTxnId)
                    val quad = when (existing) {
                        is Txn.Expense -> Quad(EntryKind.EXPENSE, existing.accountId, existing.amount, existing.categoryId)
                        is Txn.Income -> Quad(EntryKind.INCOME, existing.accountId, existing.amount, existing.categoryId)
                        is Txn.Transfer, null -> null // переводы здесь не редактируются
                    }
                    if (existing != null && quad != null) {
                        val (kind, accountId, amount, categoryId) = quad
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

            // R5.4: автоподстановку «первого активного счёта» (раньше она жила только в
            // производном uiState) фиксируем в самой форме — иначе счёт по умолчанию не
            // попадёт в SavedStateHandle и потеряется вместе с процессом. При редактировании
            // счёт уже задан из операции.
            if (editingTxnId == null && savedStateHandle.get<String>(SavedKeys.KIND) == null) {
                val presetAccount = initialAccountId?.let { id -> accounts.observeActive().first().firstOrNull { it.id == id } }
                form.update {
                    it.copy(
                        selectedCategoryId = initialCategoryId ?: it.selectedCategoryId,
                        selectedAccountId = presetAccount?.id ?: it.selectedAccountId,
                    )
                }
            }
            if (editingTxnId == null && form.value.selectedAccountId == null) {
                accounts.observeActive().first().firstOrNull()?.let { default ->
                    form.update { if (it.selectedAccountId == null) it.copy(selectedAccountId = default.id) else it }
                }
            }

            // R5.4: каждое изменение формы зеркалится в SavedStateHandle — переживает и
            // сворачивание с пересозданием процесса, и обычный поворот экрана.
            form.collect(::persistForm)
        }
    }

    /**
     * R5.4: сумма хранится как её [AmountInput.displayText] — тот же приём, что уже используется
     * при смене счёта в [selectAccount] (`AmountInput.fromText(f.amount.displayText(), ...)`),
     * только валюта здесь берётся из счёта, восстановленного из того же SavedStateHandle.
     */
    private suspend fun restoreFormFromSavedState(): Form {
        val kind = savedStateHandle.get<String>(SavedKeys.KIND)?.let(EntryKind::valueOf) ?: EntryKind.EXPENSE
        val accountId = savedStateHandle.get<String>(SavedKeys.ACCOUNT_ID)
        val account = accountId?.let { accounts.getById(it) }
        val currency = account?.let {
            currencies.getByCode(it.currency)
                ?: Currency(it.currency, minorUnits = 2, displayScale = 2, symbol = it.currency.code)
        }
        val amountText = savedStateHandle.get<String>(SavedKeys.AMOUNT_TEXT).orEmpty()
        return Form(
            kind = kind,
            amount = if (currency != null) AmountInput.fromText(amountText, currency) else AmountInput.empty(),
            calcAcc = savedStateHandle.get<Long>(SavedKeys.CALC_ACC),
            calcOp = savedStateHandle.get<String>(SavedKeys.CALC_OP)?.let(CalcOp::valueOf),
            selectedAccountId = accountId,
            selectedCategoryId = savedStateHandle.get<String>(SavedKeys.CATEGORY_ID),
            date = savedStateHandle.get<String>(SavedKeys.DATE)?.let(LocalDate::parse) ?: today(),
            note = savedStateHandle.get<String>(SavedKeys.NOTE).orEmpty(),
        )
    }

    private data class Quad(
        val kind: EntryKind,
        val accountId: String,
        val amount: Money,
        val categoryId: String?,
    )

    /**
     * R2.2/A2.11: «частые» — отдельный поток от окна последних [FREQUENT_ENTRIES_WINDOW_DAYS] дней, пересчитывается
     * только при изменении операций, а не на каждую нажатую цифру (раньше `frequentEntries` считался внутри `combine`
     * вместе с `form`). Только для нового ввода — при редактировании строка «частые» не нужна.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val frequent: Flow<List<FrequentEntry>> = if (editingTxnId == null) {
        val now = today()
        txns.observeRange(now.minusDays(FREQUENT_ENTRIES_WINDOW_DAYS), now)
            .map { frequentEntries(it, now, FREQUENT_ENTRY_SUGGESTIONS_LIMIT) }
            .distinctUntilChanged()
    } else {
        kotlinx.coroutines.flow.flowOf(emptyList())
    }

    val uiState: StateFlow<TxnEntryUiState> = combine(
        form,
        accounts.observeAll(),
        currencies.observeAll(),
        categories.observeAllForLookup(),
        frequent,
    ) { f, allAccounts, allCurrencies, allCategories, frequentList ->
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
        val activeAccountsById = allAccounts.filterNot { it.isArchived }.associateBy { it.id }
        val categoryNamesById = allCategories.associate { it.id to it.name }
        val frequentOptions = frequentList
            .mapNotNull { entry ->
                val account = activeAccountsById[entry.accountId] ?: return@mapNotNull null
                val currencyMeta = byCode[entry.currencyCode]
                    ?: Currency(CurrencyCode(entry.currencyCode), minorUnits = 2, displayScale = 2, symbol = entry.currencyCode)
                val categoryName = entry.categoryId?.let(categoryNamesById::get)
                FrequentOption(
                    entry = entry,
                    label = categoryName?.let { "$it · ${account.name}" } ?: account.name,
                    amountText = MoneyFormatter.format(Money(Minor(entry.amountMinor), CurrencyCode(entry.currencyCode)), currencyMeta),
                )
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
            frequentOptions = frequentOptions,
            saving = f.saving,
            calcOverflow = f.calcOverflow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnEntryUiState(date = form.value.date))

    fun setKind(kind: EntryKind) = form.update { it.copy(kind = kind, selectedCategoryId = null) }

    /**
     * R2.2: тап по «частым» — подставляет вид/счёт/категорию/сумму целиком, остаётся нажать
     * «Сохранить». Молча ничего не делает, если счёт шаблона уже недоступен (архивирован/удалён).
     */
    fun applyFrequent(option: FrequentOption) {
        val entry = option.entry
        val account = uiState.value.accounts.firstOrNull { it.id == entry.accountId } ?: return
        val kind = when (entry.kind) {
            FrequentEntryKind.EXPENSE -> EntryKind.EXPENSE
            FrequentEntryKind.INCOME -> EntryKind.INCOME
        }
        form.update {
            it.copy(
                kind = kind,
                selectedAccountId = entry.accountId,
                selectedCategoryId = entry.categoryId,
                amount = AmountInput.fromMinor(Minor(entry.amountMinor), account.currency),
                calcAcc = null,
                calcOp = null,
            )
        }
    }

    /** F2.8: набор новой цифры после удержанного результата ≤ 0 начинает ввод заново. */
    private fun Form.clearHeldResult(): Form =
        (if (calcOp == null && calcAcc != null) copy(calcAcc = null) else this).copy(calcOverflow = false)

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
                applyCalcOrNull(Minor(f.calcAcc), f.calcOp, operand, currency.minorUnits)
                    ?: return@update f.copy(calcOverflow = true)
            } else {
                operand
            }
            f.copy(amount = AmountInput.empty(), calcAcc = newAcc.raw, calcOp = op, calcOverflow = false)
        }
    }

    fun pressEquals() {
        val currency = uiState.value.currency ?: return
        form.update { f ->
            if (f.calcAcc == null || f.calcOp == null) return@update f
            val operand = f.amount.toMinorOrNull(currency) ?: Minor(0)
            val result = applyCalcOrNull(Minor(f.calcAcc), f.calcOp, operand, currency.minorUnits)
                ?: return@update f.copy(calcOverflow = true)
            // F2.8: результат ≤ 0 — держим как calcAcc без операции, показываем «−5» и блокируем
            // «Сохранить» с подсказкой; продолжение «+ 20 =» даёт «15».
            if (result.raw > 0) {
                f.copy(amount = AmountInput.fromMinor(result, currency), calcAcc = null, calcOp = null, calcOverflow = false)
            } else {
                f.copy(amount = AmountInput.empty(), calcAcc = result.raw, calcOp = null, calcOverflow = false)
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

    /** Как в Monefy: выбор категории при создании операции сразу сохраняет её (если сумма уже введена). */
    fun selectCategoryAndSave(id: String?) {
        selectCategory(id)
        viewModelScope.launch {
            // uiState выводится из формы асинхронно — ждём, пока выбор дойдёт до состояния, и только потом сохраняем.
            val state = kotlinx.coroutines.withTimeoutOrNull(1_000) { uiState.first { it.selectedCategoryId == id } }
            if (state?.canSave == true) save()
        }
    }

    fun selectDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun setNote(note: String) = form.update { it.copy(note = note) }

    /**
     * @return false, если форма невалидна ИЛИ запись уже в полёте. При успехе выставляет
     * [finished] после записи. Гвард по [TxnEntryUiState.saving] — двойной тап по «Сохранить»
     * до навигации прочь с экрана иначе создавал вторую операцию (тот же класс бага, что
     * [com.corriente.app.quick.QuickEntryViewModel] уже гасит своим `saving`).
     */
    fun save(): Boolean {
        val state = uiState.value
        if (!state.canSave || state.saving) return false
        val currency = state.currency ?: return false
        val accountId = state.selectedAccountId ?: return false
        val money = Money(state.resolvedMinor()!!, currency.code)
        val note = state.note.trim().ifBlank { null }
        form.update { it.copy(saving = true) }
        launchWrite(
            onError = { form.update { f -> f.copy(saving = false) }; uiMessage(R.string.txn_entry_error_save) },
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

    /**
     * R5.3: удаление физическое и немедленное (I-22), но экран не закрывается сразу — снекбар
     * «Операция удалена — Отменить» держит копию [UNDO_WINDOW_MS] на случай отмены. Уход с
     * экрана раньше (назад/системная кнопка) отменяет корутину вместе с ViewModel — отмена
     * тогда уже невозможна, удаление подтверждено.
     */
    fun deleteEditing() {
        val id = editingTxnId ?: return
        launchWrite(onError = { uiMessage(R.string.txn_entry_error_delete) }) {
            val existing = txns.getById(id) ?: return@launchWrite
            txns.deleteById(id)
            _pendingUndo.value = existing
            undoJob?.cancel()
            undoJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                _pendingUndo.value = null
                _finished.value = true
            }
        }
    }

    /** Отмена удаления — та же операция, тот же `id`/`createdAt`/`import_hash` (критерий приёмки). */
    fun undoDelete() {
        val txn = _pendingUndo.value ?: return
        undoJob?.cancel()
        _pendingUndo.value = null
        launchWrite(
            onError = { uiMessage(R.string.txn_entry_error_restore) },
            onSuccess = { _finished.value = true },
        ) {
            txns.restore(txn)
        }
    }

    companion object {
        /** R5.3: 5 секунд на «Отменить» (ROADMAP.md §7, R5.3). */
        const val UNDO_WINDOW_MS = 5_000L

        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            categories: CategoryRepository,
            currencies: CurrencyRepository,
            editingTxnId: String? = null,
            initialKind: EntryKind = EntryKind.EXPENSE,
            initialCategoryId: String? = null,
            initialAccountId: String? = null,
            repeatTxnId: String? = null,
        ) = viewModelFactory {
            initializer {
                // R5.4: createSavedStateHandle() — расширение на CreationExtras, доступное здесь
                // потому что NavBackStackEntry/ComponentActivity, из которых viewModel(...) берёт
                // extras, сами являются SavedStateRegistryOwner (navigation-compose 2.9.0).
                TxnEntryViewModel(
                    txns, accounts, categories, currencies, editingTxnId, initialKind,
                    initialCategoryId = initialCategoryId, initialAccountId = initialAccountId,
                    repeatTxnId = repeatTxnId,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
