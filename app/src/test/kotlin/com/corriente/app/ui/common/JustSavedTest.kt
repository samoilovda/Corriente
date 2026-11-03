package com.corriente.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** W8: «Записано — Отменить» предлагается только в окне сразу после записи. */
class JustSavedTest {

    @Test
    fun `fresh entry keeps the rest of the window`() {
        val e = JustSaved.Entry("t", "−500", savedAtMs = 10_000)
        assertEquals(5_000L, e.remainingMs(10_000))
        assertEquals(1_500L, e.remainingMs(13_500))
    }

    @Test
    fun `entry older than the window is not shown`() {
        val e = JustSaved.Entry("t", "−500", savedAtMs = 10_000)
        assertEquals(0L, e.remainingMs(15_000))
        // Запись с виджета час назад: приложение открыли позже — снекбара нет.
        assertEquals(0L, e.remainingMs(10_000 + 3_600_000))
    }

    @Test
    fun `clock going back does not extend the window`() {
        assertEquals(5_000L, JustSaved.Entry("t", "x", savedAtMs = 10_000).remainingMs(9_000))
    }
}
