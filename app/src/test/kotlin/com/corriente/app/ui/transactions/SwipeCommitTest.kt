package com.corriente.app.ui.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCommitTest {

    @Test
    fun `короткий взмах не удаляет и не повторяет`() {
        assertFalse(swipeCommits(offsetPx = -60, widthPx = 1080))
        assertFalse(swipeCommits(offsetPx = 60, widthPx = 1080))
        assertFalse(swipeCommits(offsetPx = -539, widthPx = 1080))
    }

    @Test
    fun `протянули на половину ширины — срабатывает в обе стороны`() {
        assertTrue(swipeCommits(offsetPx = -540, widthPx = 1080))
        assertTrue(swipeCommits(offsetPx = 800, widthPx = 1080))
    }

    @Test
    fun `ширина ещё не измерена — не срабатывает`() {
        assertFalse(swipeCommits(offsetPx = -500, widthPx = 0))
    }
}
