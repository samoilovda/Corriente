package com.corriente.app.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FabVisibilityTest {

    @Test
    fun `направление по индексу, затем по смещению`() {
        assertEquals(1, scrollDirection(2, 0, 3, 0))
        assertEquals(-1, scrollDirection(3, 10, 2, 400))
        assertEquals(1, scrollDirection(2, 10, 2, 40))
        assertEquals(-1, scrollDirection(2, 40, 2, 10))
        assertEquals(0, scrollDirection(2, 40, 2, 40))
    }

    @Test
    fun `вниз прячет, вверх показывает, без движения — как было`() {
        assertFalse(nextFabVisible(visible = true, direction = 1, atTop = false))
        assertTrue(nextFabVisible(visible = false, direction = -1, atTop = false))
        assertFalse(nextFabVisible(visible = false, direction = 0, atTop = false))
        assertTrue(nextFabVisible(visible = true, direction = 0, atTop = false))
    }

    @Test
    fun `у самого верха кнопки всегда видны`() {
        assertTrue(nextFabVisible(visible = false, direction = 1, atTop = true))
    }
}
