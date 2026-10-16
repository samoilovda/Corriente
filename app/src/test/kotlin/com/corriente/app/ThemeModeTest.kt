package com.corriente.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `сохранённое значение читается обратно`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
    }

    @Test
    fun `мусор и пустое значение — системная тема`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("sepia"))
    }
}
