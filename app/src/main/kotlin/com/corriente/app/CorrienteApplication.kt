package com.corriente.app

import android.app.Application
import com.corriente.app.backup.AutoBackupScheduler
import com.corriente.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CorrienteApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WidgetUpdater(this, container).start()

        // T5.1: держим расписание автобэкапа в соответствии с настройками.
        container.autoBackupSettings.config
            .onEach { AutoBackupScheduler.apply(this, it) }
            .launchIn(appScope)
    }
}
