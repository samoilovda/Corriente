package com.corriente.app.ui.autobackup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.corriente.app.CorrienteApplication
import com.corriente.app.backup.AutoBackupScheduler
import com.corriente.data.backup.AutoBackupConfig
import com.corriente.data.backup.AutoBackupSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutoBackupUiState(
    val enabled: Boolean = false,
    val folderLabel: String? = null,
    val retention: Int = 7,
    val lastRunAt: Long? = null,
    /** `"ok"` или текст ошибки; null — ещё не выполнялся. */
    val lastResult: String? = null,
)

/** T5.1: экран автобэкапа. Хранит папку (SAF-дерево) и включатель, запускает бэкап вручную. */
class AutoBackupViewModel(
    private val app: Application,
    private val settings: AutoBackupSettings,
) : AndroidViewModel(app) {

    val uiState: StateFlow<AutoBackupUiState> = settings.config.map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            // F1.3: первый бэкап после включения — отдельным one-time запросом, а не задержкой
            // в периодическом.
            if (enabled) AutoBackupScheduler.runNow(app)
        }
    }

    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModelScope.launch {
            settings.setTreeUri(uri.toString())
            settings.setEnabled(true)
            AutoBackupScheduler.runNow(app)
        }
    }

    fun setRetention(n: Int) {
        viewModelScope.launch { settings.setRetention(n) }
    }

    fun backupNow() {
        AutoBackupScheduler.runNow(app)
    }

    private fun AutoBackupConfig.toUi() = AutoBackupUiState(
        enabled = enabled,
        folderLabel = treeUri?.let { it.toUri().lastPathSegment },
        retention = retention,
        lastRunAt = lastRunAt,
        lastResult = lastResult,
    )

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CorrienteApplication
                AutoBackupViewModel(app, app.container.autoBackupSettings)
            }
        }
    }
}
