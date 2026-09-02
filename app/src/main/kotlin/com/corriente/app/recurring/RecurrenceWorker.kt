package com.corriente.app.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corriente.app.CorrienteApplication
import com.corriente.app.R
import java.time.LocalDate

/**
 * R2.4: раз в сутки материализует наступившие повторяющиеся операции. По образцу
 * [com.corriente.app.backup.AutoBackupWorker] — `CoroutineWorker`, без сети (I-24: весь путь
 * идёт через [com.corriente.data.repository.TxnRepository], сетевого кода тут нет и быть не
 * может). Сама логика — в [RecurrenceMaterializer] (тестируется без WorkManager).
 */
class RecurrenceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CorrienteApplication).container
        return try {
            RecurrenceMaterializer.materializeDue(
                today = LocalDate.now(),
                recurrences = container.recurrenceRepository,
                txns = container.txnRepository,
                autoNoteMarker = applicationContext.getString(R.string.recurring_auto_created_note),
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "recurrence"
        private const val MAX_ATTEMPTS = 3
    }
}
