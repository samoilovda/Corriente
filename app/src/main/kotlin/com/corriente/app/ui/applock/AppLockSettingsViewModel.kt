package com.corriente.app.ui.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.applock.AppLockMode
import com.corriente.data.applock.AppLockSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** R5.2: экран «Блокировка приложения» — три режима, без ничего кроме них. */
class AppLockSettingsViewModel(private val settings: AppLockSettings) : ViewModel() {

    val mode: StateFlow<AppLockMode> = settings.config
        .map { it.mode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockMode.OFF)

    fun setMode(mode: AppLockMode) {
        viewModelScope.launch { settings.setMode(mode) }
    }

    companion object {
        fun factory(settings: AppLockSettings) = viewModelFactory {
            initializer { AppLockSettingsViewModel(settings) }
        }
    }
}
