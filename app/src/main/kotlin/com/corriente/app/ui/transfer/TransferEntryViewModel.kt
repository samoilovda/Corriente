package com.corriente.app.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class TransferAccount(val id: String, val name: String, val currency: Currency)

data class TransferUiState(
    val accounts: List<TransferAccount> = emptyList(),
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val fromAmountText: String = "",
    val toAmountText: String = "",
    val rateText: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
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
) : ViewModel() {

    private data class Form(
        val fromAccountId: String? = null,
        val toAccountId: String? = null,
        val fromAmountText: String = "",
        val toAmountText: String = "",
        val rateText: String = "",
        val date: LocalDate,
        val note: String = "",
    )

    private val form = MutableStateFlow(Form(date = today()))
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished
    val isEditing: Boolean get() = editingTxnId != null

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
        accounts.observeActive(),
        currencies.observeAll(),
    ) { f, activeAccounts, allCurrencies ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val options = activeAccounts.map { a ->
            TransferAccount(a.id, a.name, byCode[a.currency.code] ?: fallback(a.currency))
        }
        val fromId = f.fromAccountId?.takeIf { id -> options.any { it.id == id } } ?: options.getOrNull(0)?.id
        // Явный выбор пользователя не трогаем (в т.ч. «тот же счёт» — это ловит canSave);
        // авто-подставляем только когда to ещё не задан.
        val toId = f.toAccountId?.takeIf { id -> options.any { it.id == id } }
            ?: options.firstOrNull { it.id != fromId }?.id
        TransferUiState(
            accounts = options,
            fromAccountId = fromId,
            toAccountId = toId,
            fromAmountText = f.fromAmountText,
            toAmountText = f.toAmountText,
            rateText = f.rateText,
            date = f.date,
            note = f.note,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferUiState(date = form.value.date))

    fun selectFrom(id: String) = form.update { it.copy(fromAccountId = id) }
    fun selectTo(id: String) = form.update { it.copy(toAccountId = id) }
    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }
    fun setNote(note: String) = form.update { it.copy(note = note) }

    fun setFromAmount(text: String) = form.update { f ->
        val rate = f.rateText.toPositiveRateOrNull()
        val recomputedTo = if (rate != null) recomputeTo(text, rate) else f.toAmountText
        f.copy(fromAmountText = text, toAmountText = recomputedTo)
    }

    fun setToAmount(text: String) = form.update { it.copy(toAmountText = text) }

    /** Курс — вспомогательный ввод: меняет сумму-приёмник, но сам не хранится (I-7а). */
    fun setRate(text: String) = form.update { f ->
        val rate = text.toPositiveRateOrNull()
        val recomputedTo = if (rate != null) recomputeTo(f.fromAmountText, rate) else f.toAmountText
        f.copy(rateText = text, toAmountText = recomputedTo)
    }

    /**
     * Курс имеет смысл только строго положительный. Клавиатура `Decimal` на части IME
     * пропускает «−» (и вставку), а отрицательная сумма-приёмник уронила бы [AmountInput.fromMinor].
     */
    private fun String.toPositiveRateOrNull(): BigDecimal? =
        toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    private fun recomputeTo(fromText: String, rate: BigDecimal): String {
        val state = uiState.value
        val fromCur = state.fromAccount?.currency ?: return state.toAmountText
        val toCur = state.toAccount?.currency ?: return state.toAmountText
        val fromMinor = AmountInput.fromText(fromText, fromCur).toMinorOrNull(fromCur) ?: return ""
        val toMinor = DealRate.applyRate(Money(fromMinor, fromCur.code), fromCur, rate, toCur)
        return AmountInput.fromMinor(toMinor, toCur).displayText()
    }

    fun save(): Boolean {
        val s = uiState.value
        if (!s.canSave) return false
        val fromCur = s.fromAccount?.currency ?: return false
        val toCur = s.toAccount?.currency ?: return false
        val fromMoney = Money(s.fromMinor ?: return false, fromCur.code)
        val toMoney = Money(s.toMinor ?: return false, toCur.code)
        val note = s.note.trim().ifBlank { null }
        viewModelScope.launch {
            if (editingTxnId != null) {
                txns.updateTransfer(editingTxnId, s.fromAccountId!!, fromMoney, s.toAccountId!!, toMoney, s.date, note)
            } else {
                txns.addTransfer(s.fromAccountId!!, fromMoney, s.toAccountId!!, toMoney, s.date, note)
            }
            _finished.value = true
        }
        return true
    }

    fun deleteEditing() {
        val id = editingTxnId ?: return
        viewModelScope.launch {
            txns.deleteById(id)
            _finished.value = true
        }
    }

    companion object {
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
