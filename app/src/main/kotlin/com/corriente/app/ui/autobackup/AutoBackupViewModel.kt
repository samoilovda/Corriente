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
import androidx.room.Room
import com.corriente.app.CorrienteApplication
import com.corriente.app.R
import com.corriente.app.backup.AutoBackupScheduler
import com.corriente.app.backup.BackupFilesResult
import com.corriente.app.backup.SafBackupFile
import com.corriente.app.backup.loadBackupFiles
import java.io.InputStream
import com.corriente.app.backup.SafBackupFolder
import com.corriente.data.backup.AutoBackupConfig
import com.corriente.data.backup.AutoBackupSettings
import com.corriente.data.backup.BackupProblem
import com.corriente.data.backup.BackupRepository
import com.corriente.data.backup.BackupSummary
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.PreMigrationBackup
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

/** Итог «Проверить файл» (R1.4) — сравнение сводки файла со сводкой текущей БД. */
sealed interface VerifyResult {
    data class Success(val fileSummary: BackupSummary, val currentSummary: BackupSummary) : VerifyResult
    /** Файл синтаксически валиден, но не проходит проверку целостности (F1.4/R1.4). */
    data class Invalid(val problems: List<BackupProblem>) : VerifyResult
    data object Failed : VerifyResult
}

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

    private val _copies = MutableStateFlow<List<PreMigrationBackup.Copy>>(emptyList())

    /** A2.15: копии БД «до миграции/восстановления» из `databases/pre-migration/`. */
    val copies: StateFlow<List<PreMigrationBackup.Copy>> = _copies

    private val _copyError = MutableStateFlow(false)
    val copyError: StateFlow<Boolean> = _copyError

    init {
        refreshCopies()
    }

    fun refreshCopies() {
        viewModelScope.launch {
            _copies.value = withContext(Dispatchers.IO) { PreMigrationBackup.listCopies(app, AppDatabase.DB_NAME) }
        }
    }

    fun consumeCopyError() {
        _copyError.value = false
    }

    /**
     * Откладывает подмену БД выбранной копией и перезапускает приложение: при старте [PreMigrationBackup.applyPendingRestore]
     * подменяет файл до открытия БД (текущая БД перед этим сохраняется отдельной копией), а Room при открытии
     * доводит старую схему до текущей обычными миграциями.
     */
    fun restoreCopy(copy: PreMigrationBackup.Copy) {
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) { PreMigrationBackup.stageRestore(app, AppDatabase.DB_NAME, copy.name) }
            if (!staged) {
                _copyError.value = true
                refreshCopies()
                return@launch
            }
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.component
            if (launch != null) app.startActivity(Intent.makeRestartActivityTask(launch))
            Runtime.getRuntime().exit(0)
        }
    }

    private val _folderError = MutableStateFlow(false)

    /** A1.3: доступ к папке потерян (удалена, разрешение отозвано) — экран предлагает выбрать её заново. */
    val folderError: StateFlow<Boolean> = _folderError

    fun refreshFiles() {
        viewModelScope.launch {
            val treeUri = settings.current().treeUri?.let(Uri::parse)
            currentTreeUri = treeUri
            if (treeUri == null) {
                // Ошибку не сбрасываем: папки нет, «недоступной» ей быть нечему, а флаг мог только что выставить
                // неудавшийся setFolder (A3.13) — первое обновление списка при открытии экрана гасило его гонкой.
                _backupFiles.value = emptyList()
                return@launch
            }
            when (val result = withContext(Dispatchers.IO) { loadBackupFiles(SafBackupFolder(app, treeUri)) }) {
                is BackupFilesResult.Files -> {
                    _folderError.value = false
                    _backupFiles.value = result.files
                }
                BackupFilesResult.FolderUnavailable -> {
                    _folderError.value = true
                    _backupFiles.value = emptyList()
                }
            }
        }
    }

    /**
     * Поток файла из папки автобэкапа для восстановления/проверки (R1.3/R1.4). Бросает, если папка
     * недоступна — вызывать только на IO внутри `try` (см. `BackupViewModel.restoreFrom`, [verify]).
     */
    fun openBackupFile(file: SafBackupFile): InputStream =
        SafBackupFolder(app, currentTreeUri ?: error(app.getString(R.string.autobackup_error_no_folder)))
            .openInputStream(file.documentId)

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying

    private val _verifyResult = MutableStateFlow<VerifyResult?>(null)
    val verifyResult: StateFlow<VerifyResult?> = _verifyResult

    /**
     * «Проверить файл» (R1.4): читает файл, валидирует, восстанавливает во временную
     * **in-memory** БД (`Room.inMemoryDatabaseBuilder`) и считает контрольные суммы — реальная
     * БД ([CorrienteApplication.container]) при этом не открывается на запись вообще.
     */
    fun verify(file: SafBackupFile) {
        _verifying.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val payload = openBackupFile(file).use { BackupRepository.parsePayload(it) }
                val problems = BackupRepository.validate(payload)
                if (problems.isNotEmpty()) {
                    VerifyResult.Invalid(problems)
                } else {
                    val tempDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
                    try {
                        val tempRepo = BackupRepository(tempDb)
                        tempRepo.restorePayload(payload)
                        val fileSummary = tempRepo.currentSummary()
                        val currentSummary = (app as CorrienteApplication).container.backupRepository.currentSummary()
                        VerifyResult.Success(fileSummary, currentSummary)
                    } finally {
                        tempDb.close()
                    }
                }
            } catch (e: Exception) {
                VerifyResult.Failed
            }
            _verifying.value = false
            _verifyResult.value = result
        }
    }

    fun consumeVerifyResult() {
        _verifyResult.value = null
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            // F1.3: первый бэкап после включения — отдельным one-time запросом, а не задержкой
            // в периодическом.
            if (enabled) AutoBackupScheduler.runNow(app)
        }
    }

    /**
     * A3.13: если постоянное разрешение на новую папку взять не удалось, папка не сохраняется и автобэкап не
     * включается (раньше ошибка глоталась, и воркер потом молча падал на `SecurityException`); разрешение на
     * прежнюю папку освобождается — лимит persisted-грантов конечен.
     */
    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!granted) {
            _folderError.value = true
            return
        }
        viewModelScope.launch {
            val previous = settings.current().treeUri
            settings.setTreeUri(uri.toString())
            settings.setEnabled(true)
            if (previous != null && previous != uri.toString()) {
                runCatching { app.contentResolver.releasePersistableUriPermission(previous.toUri(), flags) }
            }
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
