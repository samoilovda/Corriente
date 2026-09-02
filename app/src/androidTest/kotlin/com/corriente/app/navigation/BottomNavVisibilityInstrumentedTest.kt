package com.corriente.app.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.MainActivity
import com.corriente.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R5.1: панель нижней навигации видна только на четырёх корневых разделах
 * ([CorrienteDestination.isTopLevelRoute]) и скрыта на под-экранах, открытых поверх них.
 * Падает, если кто-то уберёт условие вокруг `NavigationBar` в [CorrienteNavHost] — тогда панель
 * (в т.ч. пункт «Отчёт», который не показан ни на одном под-экране «Настроек») останется видна
 * на «Валютах».
 */
@RunWith(AndroidJUnit4::class)
class BottomNavVisibilityInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun str(resId: Int) = composeRule.activity.getString(resId)

    @Test
    fun bottomNavIsHiddenOnASubScreenOpenedFromSettings() {
        // Корневой экран «Операции» (старт) — панель видна целиком.
        composeRule.onNodeWithText(str(R.string.nav_report)).assertExists()
        composeRule.onNodeWithText(str(R.string.nav_settings)).assertExists()

        composeRule.onNodeWithText(str(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()
        // «Настройки» — тоже корневой раздел: панель ещё видна.
        composeRule.onNodeWithText(str(R.string.nav_report)).assertExists()

        composeRule.onNodeWithText(str(R.string.currencies_title)).performClick()
        composeRule.waitForIdle()
        // «Валюты» — под-экран, открытый поверх «Настроек»: панели быть не должно.
        composeRule.onNodeWithText(str(R.string.nav_report)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.nav_transactions)).assertDoesNotExist()
    }
}
