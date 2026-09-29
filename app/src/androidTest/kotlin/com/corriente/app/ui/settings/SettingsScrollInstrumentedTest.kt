package com.corriente.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.MainActivity
import com.corriente.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A1.4: нижние пункты «Настроек» достижимы прокруткой, а не уходят под нижнюю панель. */
@RunWith(AndroidJUnit4::class)
class SettingsScrollInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun autoBackupItemIsReachableByScrolling() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.autobackup_title))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
