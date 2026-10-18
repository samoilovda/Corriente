package com.corriente.app.ui.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** U08: арифметика графиков проверяется отдельно от UI — в том числе на границах Long. */
class ChartGeometryTest {

    @Test
    fun `fractions of huge values do not overflow`() {
        assertEquals(listOf(0.5, 0.5), ChartGeometry.fractionsOfTotal(listOf(Long.MAX_VALUE, Long.MAX_VALUE)))
    }

    @Test
    fun `non-positive values take no share and an empty total is all zeros`() {
        assertEquals(listOf(0.25, 0.0, 0.75), ChartGeometry.fractionsOfTotal(listOf(1, -5, 3)))
        assertEquals(listOf(0.0, 0.0), ChartGeometry.fractionsOfTotal(listOf(0, 0)))
        assertTrue(ChartGeometry.fractionsOfTotal(emptyList()).isEmpty())
    }

    @Test
    fun `normalized handles negative balances and the full Long range`() {
        assertEquals(0.5, ChartGeometry.normalized(0, -100, 100), 1e-9)
        assertEquals(1.0, ChartGeometry.normalized(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE), 1e-9)
        assertEquals(0.0, ChartGeometry.normalized(Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE), 1e-9)
        assertEquals(0.0, ChartGeometry.normalized(5, 5, 5), 1e-9)
    }
}
