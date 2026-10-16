package com.corriente.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** UI-1.5: тема и «цвета из обоев» переживают запись в SharedPreferences и отражаются в потоках. */
@RunWith(AndroidJUnit4::class)
class UiPrefsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun restoreDefaults() {
        UiPrefs.setThemeMode(context, ThemeMode.SYSTEM)
        UiPrefs.setDynamicColor(context, true)
    }

    @Test
    fun themeModeIsStoredAndPublished() {
        UiPrefs.setThemeMode(context, ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, UiPrefs.themeMode(context).value)
        val raw = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("theme_mode", null)
        assertEquals("DARK", raw)
    }

    @Test
    fun dynamicColorIsStoredAndPublished() {
        UiPrefs.setDynamicColor(context, false)
        assertEquals(false, UiPrefs.dynamicColor(context).value)
        assertTrue(!context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getBoolean("dynamic_color", true))
    }
}
