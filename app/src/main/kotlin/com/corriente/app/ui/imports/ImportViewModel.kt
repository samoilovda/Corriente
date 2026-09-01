package com.corriente.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.imports.MonefyCsvParser
import com.corriente.data.imports.MonefyImportPlan
import com.corriente.data.imports.MonefyImportPlanner
import com.corriente.data.imports.MonefyImportRepository
import com.corriente.data.imports.ReviewReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Working : ImportUiState
    data class Ready(val summary: ImportSummary) : ImportUiState
    data class Done(val inserted: Int, val skipped: Int) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

/**
 * T3.3: предпросмотр импорта Monefy (dry-run). Разбор и планирование — офлайн, БД не трогают.
 * Запись — только по [confirm] после того, как пользователь увидел сводку, NEEDS_REVIEW и ошибки.
 */
class ImportViewModel(private val importer: MonefyImportRepository) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    private var pendingPlan: MonefyImportPlan? = null
    private var pendingFileName: String = "monefy.csv"

    fun preview(input: InputStream, fileName: String) {
        _state.value = ImportUiState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = runCatching {
                val csv = input.use { it.reader(Charsets.UTF_8).readText() }
                val plan = MonefyImportPlanner.plan(MonefyCsvParser.parse(csv))
                pendingPlan = plan
                pendingFileName = fileName
                ImportUiState.Ready(plan.toImportSummary())
            }.getOrElse { ImportUiState.Failed(it.message ?: "не удалось разобрать файл") }
        }
    }

    fun confirm() {
        val plan = pendingPlan ?: return
        _state.value = ImportUiState.Working
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = runCatching {
                val result = importer.import(plan, pendingFileName)
                ImportUiState.Done(result.inserted, result.skipped)
            }.getOrElse { ImportUiState.Failed(it.message ?: "ошибка записи") }
        }
    }

    fun reset() {
        pendingPlan = null
        _state.value = ImportUiState.Idle
    }

    companion object {
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
