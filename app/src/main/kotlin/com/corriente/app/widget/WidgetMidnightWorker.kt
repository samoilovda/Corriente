package com.corriente.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.corriente.app.CorrienteApplication
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * F2.2: пересчитывает снимок виджета на смене суток. Месячный итог и «сегодня» зависят от
 * даты, а [WidgetUpdater] пересчитывает только по записи в БД — 1-го числа виджет иначе
 * показывал бы траты прошлого месяца до первой операции. Воркер сам переставляет себя на
 * следующую полночь.
 */
class WidgetMidnightWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CorrienteApplication).container
        runCatching { WidgetUpdater(applicationContext, container).refreshNow() }
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "widget_midnight"

        fun schedule(context: Context, now: () -> LocalDate = LocalDate::now) {
            val zone = ZoneId.systemDefault()
            val nextMidnight = now().plusDays(1).atStartOfDay(zone)
            val delay = Duration.between(ZonedDateTime.now(zone), nextMidnight).coerceAtLeast(Duration.ofMinutes(1))
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
                    .setInitialDelay(delay)
                    .build(),
            )
        }
    }
}
