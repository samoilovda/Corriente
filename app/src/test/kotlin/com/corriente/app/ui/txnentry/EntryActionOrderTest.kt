package com.corriente.app.ui.txnentry

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryActionOrderTest {

    @Test
    fun `правша - запись слева, категория справа под большим пальцем`() {
        assertEquals(listOf(EntryAction.SAVE, EntryAction.CATEGORY), entryActionOrder(leftHanded = false))
    }

    @Test
    fun `левша - кнопки зеркально, категория слева`() {
        assertEquals(listOf(EntryAction.CATEGORY, EntryAction.SAVE), entryActionOrder(leftHanded = true))
    }
}
