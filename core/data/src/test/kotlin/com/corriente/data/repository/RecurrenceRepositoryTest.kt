package com.corriente.data.repository

import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.data.recurrence.dueDates
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A0.1: правка правила не должна снова делать «должной» уже материализованную дату. */
class RecurrenceRepositoryTest {

    private val rub = CurrencyCode("RUB")
    private val amount = Money(Minor(50_000_00), rub)
    private val txnDao = FakeTxnDao()
    private val dao = FakeRecurrenceDao()
    private val repo = RecurrenceRepository(dao, txnDao)

    private suspend fun materialized(rule: RecurrenceRule, on: LocalDate, today: LocalDate): String {
        val r = repo.create(TxnKind.EXPENSE, "cash", null, amount, "Аренда", rule, on)
        val txnId = "t-${r.id}"
        txnDao.insert(
            TxnEntity(
                id = txnId, kind = TxnKind.EXPENSE, date = on.toString(), createdAt = 0, updatedAt = 0,
                accountId = "cash", amountMinor = amount.amount.raw, currencyCode = "RUB",
                toAccountId = null, toAmountMinor = null, toCurrencyCode = null, categoryId = null,
                note = null, importBatchId = null, importHash = null,
            ),
        )
        repo.recordRun(r.id, com.corriente.data.recurrence.advance(rule, on), txnId)
        return r.id
    }

    @Test
    fun `editing the note of an every-30-days rule keeps nextRunOn`() = runTest {
        val today = LocalDate.of(2026, 9, 23)
        val id = materialized(RecurrenceRule.EveryNDays(30), today.minusDays(3), today)
        val before = repo.getById(id)!!.nextRunOn

        repo.update(id, "cash", null, amount, "Аренда квартиры", RecurrenceRule.EveryNDays(30), today)

        assertEquals(before, repo.getById(id)!!.nextRunOn)
    }

    @Test
    fun `editing amount on the day of month after it ran moves nothing back`() = runTest {
        val today = LocalDate.of(2026, 9, 23)
        val id = materialized(RecurrenceRule.DayOfMonth(23), today, today)

        repo.update(id, "cash", null, Money(Minor(60_000_00), rub), null, RecurrenceRule.DayOfMonth(23), today)

        assertEquals(LocalDate.of(2026, 10, 23), repo.getById(id)!!.nextRunOn)
    }

    @Test
    fun `changing the interval schedules strictly after the last created transaction`() = runTest {
        val today = LocalDate.of(2026, 9, 23)
        val id = materialized(RecurrenceRule.EveryNDays(30), today, today)

        repo.update(id, "cash", null, amount, null, RecurrenceRule.EveryNDays(7), today)

        val updated = repo.getById(id)!!
        assertTrue(updated.nextRunOn.isAfter(today))
        assertTrue(dueDates(updated.rule, updated.nextRunOn, today).isEmpty())
    }

    @Test
    fun `changing the interval without any transaction but nextRunOn in future stays after today`() = runTest {
        val today = LocalDate.of(2026, 9, 23)
        val r = repo.create(TxnKind.EXPENSE, "cash", null, amount, null, RecurrenceRule.DayOfMonth(1), today)
        assertTrue(r.nextRunOn.isAfter(today))

        repo.update(r.id, "cash", null, amount, null, RecurrenceRule.EveryNDays(5), today)

        assertEquals(today.plusDays(1), repo.getById(r.id)!!.nextRunOn)
    }
}
