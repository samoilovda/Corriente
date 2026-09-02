package com.corriente.app

import android.app.Application
import com.corriente.app.backup.AutoBackupScheduler
import com.corriente.app.backup.ShareBackupCache
import com.corriente.app.recurring.RecurrenceScheduler
import com.corriente.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CorrienteApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WidgetUpdater(this, container).start()

        // R2.4: воркер материализации повторяющихся операций — раз в сутки, безусловно.
        RecurrenceScheduler.apply(this)

        // R1.2: временные файлы «Отправить бэкап» старше суток чистятся на следующем запуске,
        // а не сразу после отправки — принимающее приложение читает URI асинхронно.
        appScope.launch(Dispatchers.IO) { ShareBackupCache.cleanOldFiles(ShareBackupCache.dir(this@CorrienteApplication)) }

        // T5.1: держим расписание автобэкапа в соответствии с настройками. F1.3: только по
        // изменению полей, влияющих на расписание — иначе запись статуса последнего запуска
        // (Room переизлучает весь app_setting) пересобирала бы воркер на каждом бэкапе.
        container.autoBackupSettings.config
            .map { it.enabled to it.treeUri }
            .distinctUntilChanged()
            .onEach { AutoBackupScheduler.apply(this, container.autoBackupSettings.current()) }
            .launchIn(appScope)
    }
}
