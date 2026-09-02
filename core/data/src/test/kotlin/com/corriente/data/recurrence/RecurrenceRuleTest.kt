package com.corriente.data.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R2.4 — dueDates/advance: пропуск нескольких периодов, 31-е в коротком месяце, високосный год. */
class RecurrenceRuleTest {

    @Test
    fun `every-N-days advances by the exact interval`() {
        val rule = RecurrenceRule.EveryNDays(7)
        assertEquals(LocalDate.of(2026, 1, 8), advance(rule, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `day-of-month advances to the same day next month`() {
        val rule = RecurrenceRule.DayOfMonth(15)
        assertEquals(LocalDate.of(2026, 2, 15), advance(rule, LocalDate.of(2026, 1, 15)))
    }

    // 31 января -> 28 февраля (2026 не високосный) -> снова 31 марта, а не 28-е.
    @Test
    fun `day 31 clamps in a short month and recovers the next month`() {
        val rule = RecurrenceRule.DayOfMonth(31)
        val afterJan = advance(rule, LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), afterJan)
        val afterFeb = advance(rule, afterJan)
        assertEquals(LocalDate.of(2026, 3, 31), afterFeb)
    }

    // 2028 — високосный год: 31-е января -> 29 февраля, не 28-е.
    @Test
    fun `day 31 clamps to 29 in a leap-year February`() {
        val rule = RecurrenceRule.DayOfMonth(31)
        assertEquals(LocalDate.of(2028, 2, 29), advance(rule, LocalDate.of(2028, 1, 31)))
    }

    @Test
    fun `dueDates is empty when nextRunOn is in the future`() {
        val rule = RecurrenceRule.EveryNDays(1)
        assertTrue(dueDates(rule, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 2)).isEmpty())
    }

    @Test
    fun `dueDates includes nextRunOn itself when it is today`() {
        val rule = RecurrenceRule.EveryNDays(30)
        val today = LocalDate.of(2026, 9, 2)
        assertEquals(listOf(today), dueDates(rule, today, today))
    }

    // Устройство было выключено/дата переведена вперёд на несколько периодов сразу.
    @Test
    fun `dueDates catches up multiple missed periods, one date per period`() {
        val rule = RecurrenceRule.DayOfMonth(1)
        val nextRunOn = LocalDate.of(2026, 6, 1)
        val today = LocalDate.of(2026, 9, 2)
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
            ),
            dueDates(rule, nextRunOn, today),
        )
    }

    @Test
    fun `dueDates never produces a date after today`() {
        val rule = RecurrenceRule.EveryNDays(3)
        val dates = dueDates(rule, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2))
        assertTrue(dates.all { !it.isAfter(LocalDate.of(2026, 9, 2)) })
    }

    @Test
    fun `firstOccurrenceOnOrAfter picks this months day when it has not passed yet`() {
        val rule = RecurrenceRule.DayOfMonth(20)
        assertEquals(LocalDate.of(2026, 9, 20), firstOccurrenceOnOrAfter(rule, LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `firstOccurrenceOnOrAfter rolls to next month when this months day already passed`() {
        val rule = RecurrenceRule.DayOfMonth(1)
        assertEquals(LocalDate.of(2026, 10, 1), firstOccurrenceOnOrAfter(rule, LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `every-N-days first occurrence is the start date itself`() {
        val rule = RecurrenceRule.EveryNDays(5)
        val start = LocalDate.of(2026, 9, 2)
        assertEquals(start, firstOccurrenceOnOrAfter(rule, start))
    }

    @Test
    fun `invalid rule parameters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RecurrenceRule.DayOfMonth(0) }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceRule.DayOfMonth(32) }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceRule.EveryNDays(0) }
    }
}
