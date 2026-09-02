package com.corriente.app.recurring

import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.advance
import com.corriente.data.recurrence.dueDates
import com.corriente.data.repository.RecurrenceRepository
import com.corriente.data.repository.TxnRepository
import java.time.LocalDate

/**
 * R2.4: сама логика материализации, вынесенная из [RecurrenceWorker] в чистый (не считая записи
 * в репозитории) suspend-объект — так её можно проверить `[БД]`-тестом на настоящем Room без
 * `androidx.work:work-testing` (эта зависимость не входит в закрытый список BUILD_PLAN.md §1.3,
 * а `RecurrenceWorker` сама по себе — тонкая обвязка над этой функцией под `CoroutineWorker`).
 *
 * Идемпотентность (I-19-стиль): состояние — `Recurrence.nextRunOn` в БД, не время последнего
 * запуска. Повторный вызов в тот же день с теми же данными видит `nextRunOn` уже в будущем
 * (сдвинутый предыдущим запуском) и не создаёт ничего.
 */
object RecurrenceMaterializer {

    /**
     * Создаёt операции по всем правилам, у которых наступила хотя бы одна дата (`<= [today]`,
     * никогда не авансом). [autoNoteMarker] — уже локализованная пометка «создано автоматически»
     * ([com.corriente.app.R.string.recurring_auto_created_note]), добавляется в заметку операции.
     */
    suspend fun materializeDue(
        today: LocalDate,
        recurrences: RecurrenceRepository,
        txns: TxnRepository,
        autoNoteMarker: String,
    ) {
        recurrences.getAll().forEach { rule ->
            val dates = dueDates(rule.rule, rule.nextRunOn, today)
            if (dates.isEmpty()) return@forEach

            var lastTxnId = rule.lastCreatedTxnId
            dates.forEach { date ->
                val note = if (rule.note.isNullOrBlank()) autoNoteMarker else "$autoNoteMarker — ${rule.note}"
                val txn = when (rule.kind) {
                    TxnKind.EXPENSE -> txns.addExpense(rule.accountId, rule.amount, rule.categoryId, date, note)
                    TxnKind.INCOME -> txns.addIncome(rule.accountId, rule.amount, rule.categoryId, date, note)
                    TxnKind.TRANSFER -> return@forEach // не поддерживается, гарантировано RecurrenceRepository
                }
                lastTxnId = txn.id
            }
            recurrences.recordRun(rule.id, advance(rule.rule, dates.last()), lastTxnId)
        }
    }
}
