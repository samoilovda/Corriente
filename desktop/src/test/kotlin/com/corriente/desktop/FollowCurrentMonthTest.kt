package com.corriente.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** W21: окно, открытое через границу месяца, переходит на новый месяц, только если показывало текущий. */
class FollowCurrentMonthTest {

    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)

    @Test
    fun `current month moves to the new one`() {
        assertEquals(oct, followCurrentMonth(shown = sep, previousCurrent = sep, current = oct))
    }

    @Test
    fun `month chosen by hand stays`() {
        val jun = YearMonth.of(2026, 6)
        assertEquals(jun, followCurrentMonth(shown = jun, previousCurrent = sep, current = oct))
    }
}
