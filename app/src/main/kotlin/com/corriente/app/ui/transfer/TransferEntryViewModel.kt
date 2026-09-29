package com.corriente.app.ui.transfer

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.R
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.uiMessage
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.DealRate
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class TransferAccount(
    val id: String,
    val name: String,
    val currency: Currency,
    val isArchived: Boolean = false,
)

data class TransferUiState(
    val accounts: List<TransferAccount> = emptyList(),
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val fromAmountText: String = "",
    val toAmountText: String = "",
    val rateText: String = "",
    /** A2.6: пересчёт по курсу не помещается в сумму — сумма-приёмник не менялась, поле курса подсвечивается. */
    val rateError: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** F2.5: false до первой эмиссии репозиториев. */
    val loaded: Boolean = false,
    /** Запись в полёте — экран обязан отключить «Сохранить» на это время (см. [Form.saving]). */
    val saving: Boolean = false,
) {
    val fromAccount: TransferAccount? get() = accounts.firstOrNull { it.id == fromAccountId }
    val toAccount: TransferAccount? get() = accounts.firstOrNull { it.id == toAccountId }
    val sameCurrency: Boolean get() = fromAccount?.currency?.code == toAccount?.currency?.code

    private fun minor(text: String, currency: Currency?): Minor? =
        currency?.let { AmountInput.fromText(text, it).toMinorOrNull(it) }

    val fromMinor: Minor? get() = minor(fromAmountText, fromAccount?.currency)
    val toMinor: Minor? get() = if (sameCurrency) fromMinor else minor(toAmountText, toAccount?.currency)

    /**
     * Курс сделки, выведенный из двух введённых сумм (I-12) — показывается под полями.
     * Направление выбирается «крупная за 1 мелкой»: для RUB→USD это «1 USD = 86.95 RUB».
     */
    val derivedRateLabel: String?
        get() {
            if (sameCurrency) return null
            val from = fromAccount?.currency ?: return null
            val to = toAccount?.currency ?: return null
            val fromMoney = Money(fromMinor ?: return null, from.code)
            val toMoney = Money(toMinor ?: return null, to.code)
            val forward = DealRate.rate(fromMoney, from, toMoney, to) ?: return null
            return if (forward < java.math.BigDecimal.ONE) {
                DealRate.format(toMoney, to, fromMoney, from)
            } else {
                DealRate.format(fromMoney, from, toMoney, to)
            }
        }

    val canSave: Boolean
        get() {
            val from = fromAccountId ?: return false
            val to = toAccountId ?: return false
            if (from == to) return false
            return (fromMinor?.raw ?: 0) > 0 && (toMinor?.raw ?: 0) > 0
        }
}

class TransferEntryViewModel(
    private val txns: TxnRepository,
    private val accounts: AccountRepository,
    private val currencies: CurrencyRepository,
    private val editingTxnId: String? = null,
    today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    private data class Form(
        val fromAccountId: String? = null,
        val toAccountId: String? = null,
        val fromAmountText: String = "",
        val toAmountText: String = "",
        val rateText: String = "",
        val rateError: Boolean = false,
        val date: LocalDate,
        val note: String = "",
        /** Двойной тап «Сохранить» до навигации прочь с экрана иначе создавал второй перевод —
         *  тот же класс бага, что [com.corriente.app.quick.QuickEntryViewModel] уже гасит. */
        val saving: Boolean = false,
    )

    private val form = MutableStateFlow(Form(date = today()))
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished
    val isEditing: Boolean get() = editingTxnId != null

    /** R5.3: как в TxnEntryViewModel — копия удалённого перевода на время снекбара «Отменить». */
    private val _pendingUndo = MutableStateFlow<Txn?>(null)
    val pendingUndo: StateFlow<Txn?> = _pendingUndo
    private var undoJob: Job? = null

    init {
        if (editingTxnId != null) {
            viewModelScope.launch {
                val existing = txns.getById(editingTxnId) as? Txn.Transfer ?: return@launch
                val fromCur = currencies.getByCode(existing.fromAmount.currency) ?: fallback(existing.fromAmount.currency)
                val toCur = currencies.getByCode(existing.toAmount.currency) ?: fallback(existing.toAmount.currency)
                form.value = Form(
                    fromAccountId = existing.fromAccountId,
                    toAccountId = existing.toAccountId,
                    fromAmountText = AmountInput.fromMinor(existing.fromAmount.amount, fromCur).displayText(),
                    toAmountText = AmountInput.fromMinor(existing.toAmount.amount, toCur).displayText(),
                    date = existing.date,
                    note = existing.note.orEmpty(),
                )
            }
        }
    }

    val uiState: StateFlow<TransferUiState> = combine(
        form,
        accounts.observeAll(),
        currencies.observeAll(),
    ) { f, allAccounts, allCurrencies ->
        val byCode = allCurrencies.associateBy { it.code.code }
        // F0.3: активные счета плюс архивные стороны редактируемого перевода — иначе правка
        // заметки уводила бы обе стороны на первые активные счета.
        val editingIds = if (editingTxnId != null) listOfNotNull(f.fromAccountId, f.toAccountId) else emptyList()
        val options = (
            allAccounts.filterNot { it.isArchived } +
                allAccounts.filter { it.isArchived && it.id in editingIds }
            ).map { a ->
                TransferAccount(a.id, a.name, byCode[a.currency.code] ?: fallback(a.currency), a.isArchived)
            }
        val fromId = when {
            f.fromAccountId != null && options.any { it.id == f.fromAccountId } -> f.fromAccountId
            editingTxnId == null -> options.getOrNull(0)?.id
            else -> null
        }
        // Явный выбор пользователя не трогаем (в т.ч. «тот же счёт» — это ловит canSave);
        // авто-подставляем только когда to ещё не задан и это создание.
        val toId = when {
            f.toAccountId != null && options.any { it.id == f.toAccountId } -> f.toAccountId
            editingTxnId == null -> options.firstOrNull { it.id != fromId }?.id
            else -> null
        }
        TransferUiState(
            accounts = options,
            fromAccountId = fromId,
            toAccountId = toId,
            fromAmountText = f.fromAmountText,
            toAmountText = f.toAmountText,
            rateText = f.rateText,
            rateError = f.rateError,
            date = f.date,
            note = f.note,
            loaded = true,
            saving = f.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferUiState(date = form.value.date))

    fun selectFrom(id: String) = form.update { it.copy(fromAccountId = id) }
    fun selectTo(id: String) = form.update { it.copy(toAccountId = id) }
    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }
    fun setNote(note: String) = form.update { it.copy(note = note) }

    fun setFromAmount(text: String) = form.update { f ->
        val rate = DealRate.parseRate(f.rateText)
        if (rate == null) return@update f.copy(fromAmountText = text, rateError = false)
        recomputeTo(f.copy(fromAmountText = text), rate)
    }

    fun setToAmount(text: String) = form.update { it.copy(toAmountText = text, rateError = false) }

    /** Курс — вспомогательный ввод: меняет сумму-приёмник, но сам не хранится (I-7а). «86,95» и «86.95» — одно и то же. */
    fun setRate(text: String) = form.update { f ->
        val rate = DealRate.parseRate(text)
        if (rate == null) return@update f.copy(rateText = text, rateError = false)
        recomputeTo(f.copy(rateText = text), rate)
    }

    /**
     * Пересчитывает сумму-приёмник по [rate]. Переполнение (A2.6) — не исключение: сумма-приёмник остаётся
     * прежней, выставляется [Form.rateError]. Вызывается внутри `form.update {}` на главном потоке.
     */
    private fun recomputeTo(f: Form, rate: BigDecimal): Form {
        val state = uiState.value
        val fromCur = state.fromAccount?.currency ?: return f.copy(rateError = false)
        val toCur = state.toAccount?.currency ?: return f.copy(rateError = false)
        val fromMinor = AmountInput.fromText(f.fromAmountText, fromCur).toMinorOrNull(fromCur)
            ?: return f.copy(toAmountText = "", rateError = false)
        return try {
            val toMinor = DealRate.applyRate(Money(fromMinor, fromCur.code), fromCur, rate, toCur)
            f.copy(toAmountText = AmountInput.fromMinor(toMinor, toCur).displayText(), rateError = false)
        } catch (e: ArithmeticException) {
            f.copy(rateError = true)
        }
    }

    fun save(): Boolean {
        val s = uiState.value
        if (!s.canSave || s.saving) return false
        val fromCur = s.fromAccount?.currency ?: return false
        val toCur = s.toAccount?.currency ?: return false
        val fromMoney = Money(s.fromMinor ?: return false, fromCur.code)
        val toMoney = Money(s.toMinor ?: return false, toCur.code)
        val note = s.note.trim().ifBlank { null }
        form.update { it.copy(saving = true) }
        launchWrite(
            onError = { form.update { f -> f.copy(saving = false) }; uiMessage(R.string.transfer_error_save) },
            onSuccess = { _finished.value = true },
        ) {
            if (editingTxnId != null) {
                txns.updateTransfer(editingTxnId, s.fromAccountId!!, fromMoney, s.toAccountId!!, toMoney, s.date, note)
            } else {
                txns.addTransfer(s.fromAccountId!!, fromMoney, s.toAccountId!!, toMoney, s.date, note)
            }
        }
        return true
    }

    /** R5.3: см. TxnEntryViewModel.deleteEditing — тот же снекбар-таймер, тот же I-22. */
    fun deleteEditing() {
        val id = editingTxnId ?: return
        launchWrite(onError = { uiMessage(R.string.transfer_error_delete) }) {
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

    fun undoDelete() {
        val txn = _pendingUndo.value ?: return
        undoJob?.cancel()
        _pendingUndo.value = null
        launchWrite(
            onError = { uiMessage(R.string.transfer_error_restore) },
            onSuccess = { _finished.value = true },
        ) {
            txns.restore(txn)
        }
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L

        private fun fallback(code: CurrencyCode) = Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

        fun factory(
            txns: TxnRepository,
            accounts: AccountRepository,
            currencies: CurrencyRepository,
            editingTxnId: String? = null,
        ) = viewModelFactory {
            initializer { TransferEntryViewModel(txns, accounts, currencies, editingTxnId) }
        }
    }
}
