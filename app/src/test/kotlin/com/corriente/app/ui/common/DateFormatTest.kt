package com.corriente.app.ui.common

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatTest {

    private val ru = Locale.forLanguageTag("ru")
    private val en = Locale.ENGLISH
    private val today = LocalDate.of(2026, 10, 17)

    private fun header(date: LocalDate, locale: Locale) =
        formatDayHeader(date, today, locale, todayLabel = "Сегодня", yesterdayLabel = "Вчера")

    @Test
    fun `сегодня и вчера — словами`() {
        assertEquals("Сегодня", header(today, ru))
        assertEquals("Вчера", header(today.minusDays(1), ru))
    }

    @Test
    fun `вчера через границу месяца и года`() {
        val firstOfMonth = LocalDate.of(2026, 10, 1)
        assertEquals("Вчера", formatDayHeader(LocalDate.of(2026, 9, 30), firstOfMonth, ru, "Сегодня", "Вчера"))
        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals("Вчера", formatDayHeader(LocalDate.of(2026, 12, 31), newYear, ru, "Сегодня", "Вчера"))
    }

    @Test
    fun `в текущем году — день недели и месяц`() {
        assertEquals("пт, 9 октября", header(LocalDate.of(2026, 10, 9), ru))
        assertEquals("Fri, 9 October", header(LocalDate.of(2026, 10, 9), en))
    }

    @Test
    fun `в другом году — с годом`() {
        assertEquals("17 октября 2025", header(LocalDate.of(2025, 10, 17), ru))
        assertEquals("17 October 2025", header(LocalDate.of(2025, 10, 17), en))
    }

    @Test
    fun `месяц — именительный падеж с заглавной`() {
        assertEquals("Октябрь 2026", formatMonth(YearMonth.of(2026, 10), ru))
        assertEquals("October 2026", formatMonth(YearMonth.of(2026, 10), en))
    }

    @Test
    fun `короткая дата — год только если не текущий`() {
        assertEquals("9 Oct", formatShortDate(LocalDate.of(2026, 10, 9), today, en))
        assertEquals("9 Oct 2025", formatShortDate(LocalDate.of(2025, 10, 9), today, en))
    }
}
