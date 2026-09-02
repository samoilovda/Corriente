package com.corriente.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.imports.MonefyCsvParser
import com.corriente.data.imports.MonefyImportPlan
import com.corriente.data.imports.MonefyImportPlanner
import com.corriente.data.imports.MonefyImportReport
import com.corriente.data.imports.MonefyImportRepository
import com.corriente.data.imports.ReviewDecision
import com.corriente.data.imports.ReviewReason
import com.corriente.data.imports.ReviewRef
import com.corriente.data.imports.applyReviewDecisions
import com.corriente.data.imports.ref
import com.corriente.data.seed.ISO_CURRENCIES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

data class ImportSummary(
    val accounts: Int,
    val openingBalances: Int,
    val categories: Int,
    val operations: Int,
    val transfers: Int,
    val unpairedHalves: Int,
    val reviews: List<ReviewLine>,
    val errors: List<String>,
)

data class ReviewLine(val reason: ReviewReason, val message: String)

/**
 * Карточка NEEDS_REVIEW для экрана dry-run: текст планировщика + текущее решение пользователя
 * ([decision] == null — вариант планировщика по умолчанию) + суммы перевода для полей ввода.
 */
data class ReviewCard(
    val ref: ReviewRef,
    val reason: ReviewReason,
    val message: String,
    val decision: ReviewDecision?,
    val fromAmountMinor: Long?,
    val fromCurrency: String?,
    val fromMinorUnits: Int,
    val toAmountMinor: Long?,
    val toCurrency: String?,
    val toMinorUnits: Int,
    val currencyChoices: List<String>,
)

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Working : ImportUiState
    data class Ready(val summary: ImportSummary, val reviews: List<ReviewCard>) : ImportUiState
    data class Done(val inserted: Int, val skipped: Int) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

/**
 * T3.3: предпросмотр импорта Monefy (dry-run). Разбор и планирование — офлайн, БД не трогают.
 * Позиции NEEDS_REVIEW можно разрешить прямо здесь ([chooseDecision]) — решение применяется
 * к плану до записи. Запись — только по [confirm].
 */
class ImportViewModel(private val importer: MonefyImportRepository) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var plan: MonefyImportPlan? = null
    private var fileName: String = "monefy.csv"
    private val decisions = mutableMapOf<ReviewRef, ReviewDecision>()

    fun preview(input: InputStream, fileName: String) {
        _state.value = ImportUiState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = runCatching {
                val csv = input.use { it.reader(Charsets.UTF_8).readText() }
                plan = MonefyImportPlanner.plan(
                    MonefyCsvParser.parse(csv),
                    existingAccounts = importer.existingAccountCurrencies(),
                )
                this@ImportViewModel.fileName = fileName
                decisions.clear()
                readyState()
            }.getOrElse { ImportUiState.Failed(it.message ?: "не удалось разобрать файл") }
        }
    }

    /** Решение по позиции NEEDS_REVIEW; `null` — вернуть вариант планировщика. */
    fun chooseDecision(ref: ReviewRef, decision: ReviewDecision?) {
        if (decision == null) decisions.remove(ref) else decisions[ref] = decision
        _state.value = readyState()
    }

    fun confirm() {
        val p = plan ?: return
        _state.value = ImportUiState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = runCatching {
                val finalPlan = p.applyReviewDecisions(decisions)
                val summary = finalPlan.toImportSummary()
                val report = MonefyImportReport(
                    accounts = summary.accounts,
                    categories = summary.categories,
                    operations = summary.operations,
                    transfers = summary.transfers,
                    unpairedHalves = summary.unpairedHalves,
                    reviews = summary.reviews.size,
                    errors = summary.errors.size,
                )
                val result = importer.import(finalPlan, fileName, report.encode())
                ImportUiState.Done(result.inserted, result.skipped)
            }.getOrElse { ImportUiState.Failed(it.message ?: "ошибка записи") }
        }
    }

    fun reset() {
        plan = null
        decisions.clear()
        _state.value = ImportUiState.Idle
    }

    private fun readyState(): ImportUiState.Ready {
        val p = plan ?: return ImportUiState.Ready(EMPTY_SUMMARY, emptyList())
        return ImportUiState.Ready(
            summary = p.applyReviewDecisions(decisions).toImportSummary(),
            reviews = reviewCards(p, decisions),
        )
    }

    companion object {
        private val EMPTY_SUMMARY = ImportSummary(0, 0, 0, 0, 0, 0, emptyList(), emptyList())

        fun factory(importer: MonefyImportRepository) = viewModelFactory {
            initializer { ImportViewModel(importer) }
        }
    }
}

/** Сводка dry-run для экрана. Чистая функция над планом — тестируется без БД. */
internal fun MonefyImportPlan.toImportSummary() = ImportSummary(
    accounts = accounts.size,
    openingBalances = accounts.count { it.openingBalanceMinor != 0L },
    categories = categories.size,
    operations = plainTxns.count { !it.unpairedHalf },
    transfers = transfers.size,
    unpairedHalves = plainTxns.count { it.unpairedHalf },
    reviews = reviews.map { ReviewLine(it.reason, it.message) },
    errors = errors.map { "строка ${it.line}: ${it.reason}" },
)

/** Карточки для интерактивного разрешения NEEDS_REVIEW — над ИСХОДНЫМ планом (список стабилен). */
internal fun reviewCards(plan: MonefyImportPlan, decisions: Map<ReviewRef, ReviewDecision>): List<ReviewCard> {
    val minorUnitsByCode = ISO_CURRENCIES.associate { it.code to it.minorUnits }
    fun minorUnits(code: String?) = code?.let { minorUnitsByCode[it] } ?: 2
    return plan.reviews.map { item ->
        val ref = item.ref()
        val tx = plan.transfers.firstOrNull { it.fromLine in item.lines && it.toLine in item.lines }
        ReviewCard(
            ref = ref,
            reason = item.reason,
            message = item.message,
            decision = decisions[ref],
            fromAmountMinor = tx?.fromAmountMinor,
            fromCurrency = tx?.fromCurrency?.code,
            fromMinorUnits = minorUnits(tx?.fromCurrency?.code),
            toAmountMinor = tx?.toAmountMinor,
            toCurrency = tx?.toCurrency?.code,
            toMinorUnits = minorUnits(tx?.toCurrency?.code),
            currencyChoices = item.currencyChoices.map { it.code },
        )
    }
}
