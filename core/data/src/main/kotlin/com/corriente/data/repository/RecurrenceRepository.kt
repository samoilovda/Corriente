package com.corriente.data.repository

import com.corriente.data.db.dao.RecurrenceDao
import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Recurrence
import com.corriente.data.model.toDomain
import com.corriente.data.model.toEntity
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.data.recurrence.firstOccurrenceOnOrAfter
import com.corriente.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Повторяющиеся операции (R2.4). Единственное правило, которое здесь enforced (Room не умеет
 * CHECK — тот же T0.4-стиль, что в [TxnRepository]/[BudgetRepository]): перевод не поддерживается
 * шаблоном — у него две стороны, а "аренда/подписка/зарплата" в этом не нуждаются.
 */
class RecurrenceRepository(private val dao: RecurrenceDao, private val txnDao: TxnDao) {

    fun observeAll(): Flow<List<Recurrence>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Recurrence? = dao.getById(id)?.toDomain()

    /** Для воркера (R2.4) — не подписка, разовое чтение всего набора правил. */
    suspend fun getAll(): List<Recurrence> = dao.getAll().map { it.toDomain() }

    suspend fun create(
        kind: TxnKind,
        accountId: String,
        categoryId: String?,
        amount: Money,
        note: String?,
        rule: RecurrenceRule,
        startsOn: LocalDate,
    ): Recurrence {
        require(kind != TxnKind.TRANSFER) { "Recurring transfers are not supported" }
        require(amount.amount.raw > 0) { "Recurrence amount must be positive" }
        val recurrence = Recurrence(
            id = UUID.randomUUID().toString(),
            kind = kind,
            accountId = accountId,
            categoryId = categoryId,
            amount = amount,
            note = note,
            rule = rule,
            nextRunOn = firstOccurrenceOnOrAfter(rule, startsOn),
            lastCreatedTxnId = null,
        )
        dao.insert(recurrence.toEntity())
        return recurrence
    }

    /**
     * Правка шаблона (R2.4, A0.1). Инвариант: после правки ни одна дата ≤ даты последней созданной
     * правилом операции не может снова оказаться в `dueDates`.
     *
     * Правило (тип + параметр) не изменилось — [Recurrence.nextRunOn] не трогаем: правка суммы, заметки,
     * счёта, категории действует со следующего запуска. Изменилось — расписание пересчитывается от
     * `max(today, дата последней созданной операции + 1)`; если операции нет, но сегодняшняя дата уже
     * обработана (`nextRunOn > today`), — от `today + 1`.
     */
    suspend fun update(
        id: String,
        accountId: String,
        categoryId: String?,
        amount: Money,
        note: String?,
        rule: RecurrenceRule,
        today: LocalDate,
    ) {
        require(amount.amount.raw > 0) { "Recurrence amount must be positive" }
        val existing = requireNotNull(dao.getById(id)) { "Recurrence $id not found" }.toDomain()
        val nextRunOn = if (rule == existing.rule) {
            existing.nextRunOn
        } else {
            val lastDate = existing.lastCreatedTxnId
                ?.let { txnDao.getById(it) }
                ?.let { LocalDate.parse(it.date) }
            var lowerBound = today
            if (lastDate != null && !lastDate.isBefore(lowerBound)) lowerBound = lastDate.plusDays(1)
            if (lastDate == null && existing.nextRunOn.isAfter(today)) lowerBound = today.plusDays(1)
            firstOccurrenceOnOrAfter(rule, lowerBound)
        }
        val updated = existing.copy(
            accountId = accountId,
            categoryId = categoryId,
            amount = amount,
            note = note,
            rule = rule,
            nextRunOn = nextRunOn,
        )
        dao.update(updated.toEntity())
    }

    suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    /** Воркер (R2.4) сдвигает указатель и запоминает последнюю созданную операцию. */
    suspend fun recordRun(id: String, nextRunOn: LocalDate, lastCreatedTxnId: String?) {
        dao.recordRun(id, nextRunOn.toString(), lastCreatedTxnId)
    }
}
