package com.corriente.app.recurring

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.corriente.data.model.Recurrence
import java.time.LocalDate
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

    /**
     * W7: материализация сейчас — при открытии приложения и когда появилось правило с наступившей датой. Без этого
     * платёж «5-го» появлялся, когда WorkManager запустит суточную задачу (до суток позже), а правило «на сегодня» не
     * давало операции сразу. Идемпотентно: id операций детерминированы (I-27), `nextRunOn` уже сдвинут.
     */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<RecurrenceWorker>().build(),
        )
    }

    private const val UNIQUE_NOW = "recurrence-now"
}

/** W7: есть ли правило, чья дата уже наступила, — тогда материализацию запускаем сразу, не дожидаясь суточной. */
fun anyRecurrenceDue(rules: List<Recurrence>, today: LocalDate): Boolean = rules.any { !it.nextRunOn.isAfter(today) }
