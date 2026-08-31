package com.corriente.app.ui.report

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReportPeriodTest {

    private val may15 = LocalDate.of(2026, 5, 15)

    @Test
    fun `month range spans the whole calendar month`() {
        val r = periodRange(PeriodMode.MONTH, may15)
        assertEquals(LocalDate.of(2026, 5, 1), r.start)
        assertEquals(LocalDate.of(2026, 5, 31), r.endInclusive)
    }

    @Test
    fun `quarter range spans three months`() {
        val r = periodRange(PeriodMode.QUARTER, may15) // Q2
        assertEquals(LocalDate.of(2026, 4, 1), r.start)
        assertEquals(LocalDate.of(2026, 6, 30), r.endInclusive)
        val q1 = periodRange(PeriodMode.QUARTER, LocalDate.of(2026, 2, 9))
        assertEquals(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 3, 31), q1)
    }

    @Test
    fun `year range spans jan to dec`() {
        val r = periodRange(PeriodMode.YEAR, may15)
        assertEquals(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31), r)
    }

    @Test
    fun `custom range normalises reversed dates`() {
        val r = periodRange(PeriodMode.CUSTOM, may15, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1))
        assertEquals(LocalDate.of(2026, 6, 1)..LocalDate.of(2026, 6, 10), r)
    }

    @Test
    fun `shiftAnchor steps by the period size`() {
        assertEquals(LocalDate.of(2026, 4, 1), shiftAnchor(PeriodMode.MONTH, may15, -1))
        assertEquals(LocalDate.of(2026, 8, 1), shiftAnchor(PeriodMode.QUARTER, may15, 1))
        assertEquals(LocalDate.of(2025, 5, 1), shiftAnchor(PeriodMode.YEAR, may15, -1))
        assertEquals(may15, shiftAnchor(PeriodMode.CUSTOM, may15, 3))
    }

    @Test
    fun `label reads sensibly for each mode`() {
        assertEquals("05.2026", periodLabel(PeriodMode.MONTH, periodRange(PeriodMode.MONTH, may15)))
        assertEquals("Q2 2026", periodLabel(PeriodMode.QUARTER, periodRange(PeriodMode.QUARTER, may15)))
        assertEquals("2026", periodLabel(PeriodMode.YEAR, periodRange(PeriodMode.YEAR, may15)))
    }
}
