package com.corriente.app.recurring

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * R2.4: планирование материализации повторяющихся операций — раз в сутки, безусловно (в отличие
 * от автобэкапа это не настройка пользователя: правила либо есть, либо нет, а проверка "есть ли
 * хоть одно правило" дешевле внутри воркера, чем условное включение/выключение расписания).
 */
object RecurrenceScheduler {

    fun apply(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecurrenceWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RecurrenceWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
