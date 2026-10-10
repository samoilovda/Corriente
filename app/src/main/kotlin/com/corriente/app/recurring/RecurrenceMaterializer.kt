package com.corriente.app.recurring

import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.MAX_CATCH_UP_DATES
import com.corriente.data.recurrence.RecurrenceErrors
import com.corriente.data.recurrence.advance
import com.corriente.data.recurrence.dueDates
import com.corriente.data.recurrence.recurrenceTxnId
import com.corriente.data.repository.RecurrenceRepository
import com.corriente.data.repository.TxnRepository
import kotlin.coroutines.cancellation.CancellationException
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
     * Создаёт операции по всем правилам, у которых наступила хотя бы одна дата (`<= [today]`,
     * никогда не авансом). [autoNoteMarker] — уже локализованная пометка «создано автоматически»
     * ([com.corriente.app.R.string.recurring_auto_created_note]), добавляется в заметку операции.
     *
     * A1.5: каждое правило исполняется изолированно и атомарно — все его операции и сдвиг
     * `nextRunOn` идут в одной транзакции [atomically] (в приложении — `db.withTransaction`), а
     * ошибка одного правила ([onResult] получает текст) не мешает остальным. Возвращает число правил,
     * завершившихся ошибкой.
     */
    suspend fun materializeDue(
        today: LocalDate,
        recurrences: RecurrenceRepository,
        txns: TxnRepository,
        autoNoteMarker: String,
        atomically: suspend (suspend () -> Unit) -> Unit = { it() },
        onResult: suspend (ruleId: String, error: String?) -> Unit = { _, _ -> },
        /** A3.8: `true` — счёт правила в архиве, правило пропускается с пометкой (в скрытый счёт не пишем). */
        isAccountArchived: suspend (accountId: String) -> Boolean = { false },
    ): Int {
        var failed = 0
        recurrences.getAll().forEach { rule ->
            // limit + 1: чтобы отличить «ровно limit» от «есть ещё» (A3.8)
            val candidates = dueDates(rule.rule, rule.nextRunOn, today, MAX_CATCH_UP_DATES + 1)
            if (candidates.isEmpty()) return@forEach
            if (isAccountArchived(rule.accountId)) {
                onResult(rule.id, RecurrenceErrors.CODE_ARCHIVED_ACCOUNT)
                return@forEach
            }
            val dates = candidates.take(MAX_CATCH_UP_DATES)
            val truncated = candidates.size > MAX_CATCH_UP_DATES
            try {
                atomically {
                    var lastTxnId = rule.lastCreatedTxnId
                    dates.forEach { date ->
                        val note = if (rule.note.isNullOrBlank()) autoNoteMarker else "$autoNoteMarker — ${rule.note}"
                        // I-27: id детерминирован — операция на эту дату уже есть (пришла с другого устройства или
                        // создана прошлым прерванным запуском) — не создаём вторую.
                        val id = recurrenceTxnId(rule.id, date)
                        if (txns.getById(id) != null) {
                            lastTxnId = id
                            return@forEach
                        }
                        val txn = when (rule.kind) {
                            TxnKind.EXPENSE -> txns.addExpense(rule.accountId, rule.amount, rule.categoryId, date, note, id = id)
                            TxnKind.INCOME -> txns.addIncome(rule.accountId, rule.amount, rule.categoryId, date, note, id = id)
                            TxnKind.TRANSFER -> return@forEach // не поддерживается, гарантировано RecurrenceRepository
                        }
                        lastTxnId = txn.id
                    }
                    recurrences.recordRun(rule.id, advance(rule.rule, dates.last()), lastTxnId)
                }
                onResult(rule.id, if (truncated) RecurrenceErrors.CODE_CATCH_UP_LIMITED else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                onResult(rule.id, e.message ?: e.javaClass.simpleName)
            }
        }
        return failed
    }
}
