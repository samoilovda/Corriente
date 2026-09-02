package com.corriente.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corriente.app.CorrienteApplication
import java.util.Date

/**
 * T5.1: периодический автобэкап. Пишет полный экспорт БД в выбранную папку и подрезает
 * старые файлы. На запись в сеть неспособен by design (I-24) — только SAF.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CorrienteApplication).container
        val config = container.autoBackupSettings.current()
        if (!config.enabled) return Result.success()
        val treeUri = config.treeUri?.let(Uri::parse) ?: return Result.success()

        val settings = container.autoBackupSettings
        return try {
            val folder = SafBackupFolder(applicationContext, treeUri)
            folder.writeNewBackup(Date()) { output -> container.backupRepository.export(output) }
            folder.prune(config.retention)
            settings.recordRun(System.currentTimeMillis(), RESULT_OK)
            Result.success()
        } catch (e: SecurityException) {
            // потеряли доступ к папке (пользователь отозвал разрешение) — не долбимся повторно
            settings.setEnabled(false)
            settings.recordRun(System.currentTimeMillis(), "Нет доступа к папке — автобэкап выключен")
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                settings.recordRun(System.currentTimeMillis(), e.message ?: "Не удалось сделать бэкап")
                Result.failure()
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "auto_backup"
        const val RESULT_OK = "ok"
        private const val MAX_ATTEMPTS = 3
    }
}
