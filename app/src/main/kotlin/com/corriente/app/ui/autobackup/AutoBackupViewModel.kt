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
import com.corriente.app.backup.SafBackupFile
import com.corriente.app.backup.SafBackupFolder
import com.corriente.data.backup.AutoBackupConfig
import com.corriente.data.backup.AutoBackupSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var currentTreeUri: Uri? = null
    private val _backupFiles = MutableStateFlow<List<SafBackupFile>>(emptyList())

    /** Файлы в выбранной папке (R1.3) — обновляется при смене папки и после каждого запуска бэкапа. */
    val backupFiles: StateFlow<List<SafBackupFile>> = _backupFiles

    init {
        // Перечитать список при смене папки и при обновлении lastRunAt (успешный ручной/авто
        // бэкап добавил файл) — без этого список молча устаревал бы до следующего открытия экрана.
        uiState.map { it.folderLabel to it.lastRunAt }.distinctUntilChanged()
            .onEach { refreshFiles() }
            .launchIn(viewModelScope)
    }

    fun refreshFiles() {
        viewModelScope.launch {
            val treeUri = settings.current().treeUri?.let(Uri::parse)
            currentTreeUri = treeUri
            _backupFiles.value = if (treeUri != null) {
                withContext(Dispatchers.IO) { SafBackupFolder(app, treeUri).list() }
            } else {
                emptyList()
            }
        }
    }

    /** Поток файла из папки автобэкапа для восстановления/проверки (R1.3/R1.4). */
    fun openBackupFile(file: SafBackupFile) =
        SafBackupFolder(app, currentTreeUri ?: error("папка автобэкапа не выбрана")).openInputStream(file.documentId)

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
