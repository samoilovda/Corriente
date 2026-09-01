package com.corriente.app.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.corriente.data.backup.AutoBackupConfig
import java.util.concurrent.TimeUnit

/**
 * T5.1: планирование автобэкапа. Один уникальный периодический воркер, раз в сутки
 * (`updatePeriodMillis` платформы WorkManager сам растягивает под Doze). При выключении —
 * снимаем.
 */
object AutoBackupScheduler {

    fun apply(context: Context, config: AutoBackupConfig) {
        val wm = WorkManager.getInstance(context)
        if (!config.enabled || config.treeUri == null) {
            wm.cancelUniqueWork(AutoBackupWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** «Сделать бэкап сейчас» с экрана настроек. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }
}
