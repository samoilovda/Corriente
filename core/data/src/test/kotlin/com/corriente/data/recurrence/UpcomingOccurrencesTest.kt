package com.corriente.data.recurrence

import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Recurrence
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** U12: «ближайшие операции» — то же расписание, что у материализации, без записи в учёт. */
class UpcomingOccurrencesTest {

    private fun rule(id: String, rule: RecurrenceRule, next: LocalDate, account: String = "acc") = Recurrence(
        id = id, kind = TxnKind.EXPENSE, accountId = account, categoryId = null,
        amount = Money(Minor(100_00), CurrencyCode("RUB")), note = null, rule = rule, nextRunOn = next, lastCreatedTxnId = null,
    )

    @Test
    fun `day 31 clamps to the end of February and returns to 31 in March`() {
        val r = rule("rent", RecurrenceRule.DayOfMonth(31), LocalDate.of(2028, 1, 31))
        val dates = upcomingOccurrences(listOf(r), LocalDate.of(2028, 1, 15), horizonDays = 90).map { it.date }
        assertEquals(listOf(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)), dates)
    }

    @Test
    fun `missed dates are shown as pending catch-up, once each`() {
        val r = rule("daily", RecurrenceRule.EveryNDays(1), LocalDate.of(2026, 10, 15))
        val upcoming = upcomingOccurrences(listOf(r), LocalDate.of(2026, 10, 18), horizonDays = 1)
        assertEquals(
            listOf(15, 16, 17, 18, 19).map { LocalDate.of(2026, 10, it) },
            upcoming.map { it.date },
        )
        assertEquals(listOf(true, true, true, false, false), upcoming.map { it.pending })
    }

    @Test
    fun `rules on archived accounts are skipped like the worker skips them`() {
        val active = rule("a", RecurrenceRule.EveryNDays(7), LocalDate.of(2026, 10, 20))
        val archived = rule("b", RecurrenceRule.EveryNDays(7), LocalDate.of(2026, 10, 20), account = "old")
        val ids = upcomingOccurrences(listOf(active, archived), LocalDate.of(2026, 10, 18), 30, isAccountArchived = { it == "old" })
            .map { it.recurrence.id }.distinct()
        assertEquals(listOf("a"), ids)
    }

    @Test
    fun `today is the local date of the device zone, not UTC`() {
        // Один и тот же момент: в Москве ещё 18 октября, в Апиа (UTC+13) уже 19-е — «сегодня» берётся по поясу устройства.
        val instant = ZonedDateTime.of(2026, 10, 18, 23, 30, 0, 0, ZoneId.of("Europe/Moscow")).toInstant()
        val moscowToday = instant.atZone(ZoneId.of("Europe/Moscow")).toLocalDate()
        val apiaToday = instant.atZone(ZoneId.of("Pacific/Apia")).toLocalDate()
        val r = rule("r", RecurrenceRule.DayOfMonth(19), LocalDate.of(2026, 10, 19))
        assertEquals(false, upcomingOccurrences(listOf(r), moscowToday, 0).isNotEmpty())
        assertEquals(LocalDate.of(2026, 10, 19), upcomingOccurrences(listOf(r), apiaToday, 0).single().date)
    }

    @Test
    fun `two devices with the same synced rule show the same schedule and the same deterministic ids`() {
        val r = rule("shared", RecurrenceRule.DayOfMonth(5), LocalDate.of(2026, 11, 5))
        val phone = upcomingOccurrences(listOf(r), LocalDate.of(2026, 10, 18), 60)
        val tablet = upcomingOccurrences(listOf(r.copy()), LocalDate.of(2026, 10, 18), 60)
        assertEquals(phone, tablet)
        assertEquals(
            phone.map { recurrenceTxnId(it.recurrence.id, it.date) },
            tablet.map { recurrenceTxnId(it.recurrence.id, it.date) },
        )
    }

    @Test
    fun `sorted by date across rules and bounded per rule`() {
        val monthly = rule("m", RecurrenceRule.DayOfMonth(1), LocalDate.of(2026, 11, 1))
        val daily = rule("d", RecurrenceRule.EveryNDays(1), LocalDate.of(2026, 10, 19))
        val upcoming = upcomingOccurrences(listOf(monthly, daily), LocalDate.of(2026, 10, 18), 365, perRuleLimit = 5)
        assertEquals(upcoming.sortedBy { it.date }, upcoming)
        assertEquals(5, upcoming.count { it.recurrence.id == "d" })
        assertTrue(upcoming.any { it.recurrence.id == "m" })
    }
}
