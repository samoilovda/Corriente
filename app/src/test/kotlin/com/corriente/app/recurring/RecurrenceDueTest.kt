package com.corriente.app.recurring

import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Recurrence
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** W7: материализация запускается сразу, только если у какого-то правила дата уже наступила. */
class RecurrenceDueTest {

    private val today = LocalDate.of(2026, 11, 5)

    private fun rule(next: LocalDate) = Recurrence(
        id = "r-$next", kind = TxnKind.EXPENSE, accountId = "cash", categoryId = null,
        amount = Money(Minor(100), CurrencyCode("RUB")), note = null,
        rule = RecurrenceRule.DayOfMonth(5), nextRunOn = next, lastCreatedTxnId = null,
    )

    @Test
    fun `rule due today or earlier needs a run now`() {
        assertTrue(anyRecurrenceDue(listOf(rule(today)), today))
        assertTrue(anyRecurrenceDue(listOf(rule(today.plusMonths(1)), rule(today.minusDays(3))), today))
    }

    @Test
    fun `only future rules wait for their day`() {
        assertFalse(anyRecurrenceDue(listOf(rule(today.plusDays(1))), today))
        assertFalse(anyRecurrenceDue(emptyList(), today))
    }
}
