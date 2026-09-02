package com.corriente.app.ui.imports

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.R
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.uiMessage
import com.corriente.data.imports.MonefyImportRepository
import com.corriente.data.imports.MonefyImportRepository.ImportBatchInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** F1.5: список прошлых импортов Monefy с возможностью откатить любой из них. */
class ImportHistoryViewModel(private val importer: MonefyImportRepository) : WritingViewModel() {

    val batches: StateFlow<List<ImportBatchInfo>> = importer.observeBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rollback(batchId: String) {
        launchWrite(onError = { uiMessage(R.string.import_history_error_rollback) }) {
            importer.rollback(batchId)
        }
    }

    companion object {
        fun factory(importer: MonefyImportRepository) = viewModelFactory {
            initializer { ImportHistoryViewModel(importer) }
        }
    }
}
