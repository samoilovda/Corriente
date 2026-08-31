package com.corriente.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.backup.BackupRepository
import com.corriente.data.backup.BackupVersionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

sealed interface BackupResult {
    data object Exported : BackupResult
    data object Imported : BackupResult
    data class VersionMismatch(val fileVersion: Int, val appVersion: Int) : BackupResult
    data object Failed : BackupResult
}

/**
 * Экспорт/восстановление бэкапа (T1.9). Потоки на выбранный пользователем файл открывает
 * экран (SAF/ContentResolver), ViewModel знает только про [InputStream]/[OutputStream] и
 * делегирует в [BackupRepository] — как и он сам, ничего не знает про сеть (I-24).
 */
class BackupViewModel(private val backup: BackupRepository) : ViewModel() {

    private val _result = MutableStateFlow<BackupResult?>(null)
    val result: StateFlow<BackupResult?> = _result

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun export(output: OutputStream) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _result.value = runCatching { output.use { backup.export(it) } }
                .fold(onSuccess = { BackupResult.Exported }, onFailure = { BackupResult.Failed })
            _busy.value = false
        }
    }

    fun restore(input: InputStream) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _result.value = runCatching { input.use { backup.restore(it) } }.fold(
                onSuccess = { BackupResult.Imported },
                onFailure = { e ->
                    if (e is BackupVersionException) BackupResult.VersionMismatch(e.fileVersion, e.appVersion)
                    else BackupResult.Failed
                },
            )
            _busy.value = false
        }
    }

    fun consumeResult() {
        _result.value = null
    }

    companion object {
        fun factory(backup: BackupRepository) = viewModelFactory {
            initializer { BackupViewModel(backup) }
        }
    }
}
