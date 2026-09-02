package com.corriente.app.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.corriente.data.backup.AutoBackupConfig
import java.util.concurrent.TimeUnit

/**
 * T5.1 / F1.3: планирование автобэкапа. Один уникальный периодический воркер, раз в сутки.
 *
 * F1.3: у периодического запроса больше нет `setInitialDelay` — начальная задержка при
 * повторной постановке (`UPDATE`) могла отодвигать первый запуск бесконечно. Первый бэкап
 * после включения запускается отдельным [runNow]. Плюс `setRequiresStorageNotLow` — писать
 * на переполненный диск бессмысленно.
 */
object AutoBackupScheduler {

    private val constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .build()

    fun apply(context: Context, config: AutoBackupConfig) {
        val wm = WorkManager.getInstance(context)
        if (!config.enabled || config.treeUri == null) {
            wm.cancelUniqueWork(AutoBackupWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** «Сделать бэкап сейчас» и первый бэкап сразу после включения автобэкапа. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(constraints)
                .build(),
        )
    }
}
