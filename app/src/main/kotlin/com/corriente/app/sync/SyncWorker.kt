package com.corriente.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.corriente.app.CorrienteApplication
import java.util.concurrent.TimeUnit

/** Один обмен синхронизации в фоне. Ошибки записываются в статус ([SyncController.runOnce]), воркер не повторяется. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as CorrienteApplication).container.syncController.runOnce()
        return Result.success()
    }
}

/**
 * Когда синхронизироваться (docs/SYNC_DESIGN.md §1): раз в 15 минут в фоне (минимум WorkManager), после локальной
 * записи с задержкой ~10 с (новые записи сдвигают обмен — несколько правок подряд уйдут одним файлом) и при открытии
 * приложения. Сеть не нужна: обмен — запись/чтение файлов, переносит их Syncthing.
 */
object SyncScheduler {
    private const val PERIODIC = "sync-periodic"
    private const val SOON = "sync-soon"
    const val AFTER_WRITE_DELAY_SECONDS = 10L

    fun apply(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(SOON)
            return
        }
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    fun requestSoon(context: Context, delaySeconds: Long = AFTER_WRITE_DELAY_SECONDS) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SOON,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
        )
    }
}
