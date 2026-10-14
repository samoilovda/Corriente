package com.corriente.app.ui.sync

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.CorrienteApplication
import com.corriente.app.sync.JoinMode
import com.corriente.app.sync.SyncController
import com.corriente.app.sync.SyncStatus
import com.corriente.data.sync.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Что спросить у пользователя при подключении (docs/SYNC_DESIGN.md §6). */
sealed interface JoinQuestion {
    /** В папке ещё нет ни одного устройства — подключаться не к чему. */
    data object EmptyFolder : JoinQuestion
    /** На этом устройстве свои данные: объединить или заменить. */
    data object HasLocalData : JoinQuestion
}

class SyncViewModel(app: Application, private val controller: SyncController) : AndroidViewModel(app) {

    val status: StateFlow<SyncStatus> = controller.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices

    private val _ownId = MutableStateFlow<String?>(null)
    val ownId: StateFlow<String?> = _ownId

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _question = MutableStateFlow<JoinQuestion?>(null)
    val question: StateFlow<JoinQuestion?> = _question

    /** Имя устройства по умолчанию — модель («POCO F8 Pro», «MatePad 11.5»). */
    val defaultDeviceName: String = Build.MODEL.orEmpty().ifBlank { "Android" }

    init {
        // Список устройств — после каждого обмена (и фонового тоже: новое устройство могло появиться в папке).
        status.map { it.lastRunAt to it.folderUri }.distinctUntilChanged().onEach { refreshDevices() }.launchIn(viewModelScope)
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _devices.value = controller.devices().sortedBy { it.name.lowercase() }
            _ownId.value = controller.ownDeviceId()
        }
    }

    /** A3.13-стиль: без постоянного разрешения папку не сохраняем. @return false — разрешение не выдано. */
    fun setFolder(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!granted) return false
        viewModelScope.launch {
            controller.setFolder(uri)
            refreshDevices()
        }
        return true
    }

    fun startHere(name: String) = work { controller.startHere(name.trim().ifBlank { defaultDeviceName }) }

    /** Подключение: сначала проверяем папку и свои данные — при необходимости спрашиваем ([question]). */
    fun requestJoin(name: String) = work {
        when {
            controller.devices().isEmpty() -> _question.value = JoinQuestion.EmptyFolder
            controller.hasLocalData() -> _question.value = JoinQuestion.HasLocalData
            else -> controller.join(name.trim().ifBlank { defaultDeviceName }, mode = null)
        }
    }

    fun join(name: String, mode: JoinMode) {
        _question.value = null
        work { controller.join(name.trim().ifBlank { defaultDeviceName }, mode) }
    }

    fun dismissQuestion() {
        _question.value = null
    }

    fun syncNow() = work { controller.runOnce() }

    fun cleanup() = work { controller.cleanup() }

    fun disable() = work { controller.disable() }

    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                // Ошибки обмена контроллер пишет в статус; сюда долетает только непредвиденное — не роняем экран.
                runCatching { block() }
            } finally {
                _busy.value = false
                refreshDevices()
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CorrienteApplication
                SyncViewModel(app, app.container.syncController)
            }
        }
    }
}
