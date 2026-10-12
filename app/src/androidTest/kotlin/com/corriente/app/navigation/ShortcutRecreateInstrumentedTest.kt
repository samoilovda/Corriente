package com.corriente.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.app.MainActivity
import com.corriente.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A2.8: запуск ярлыком приложения (`corriente://entry?kind=EXPENSE`) → «Назад» → «Отчёт» → пересоздание Activity
 * (поворот экрана) — остаёмся на «Отчёте», а не возвращаемся на экран ввода из ярлыка.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutRecreateInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun recreatingTheActivityDoesNotReplayTheShortcut() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("corriente://entry?kind=EXPENSE"))
            .setClassName(context.packageName, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.txn_entry_title)).assertExists()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.nav_report)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.report_open_fx)).assertExists()

            // T1: на медленном эмуляторе Activity могла ещё не стать RESUMED (идёт переход) — recreate() тогда
            // падал с «Requested a re-creation of Activity but didn't happen». Ждём устойчивого состояния.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(context.getString(R.string.report_open_fx))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()
            scenario.recreate()
            composeRule.waitForIdle()

            composeRule.onNodeWithText(context.getString(R.string.txn_entry_title)).assertDoesNotExist()
            composeRule.onNodeWithText(context.getString(R.string.report_open_fx)).assertExists()
        }
    }
}
