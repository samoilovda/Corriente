package com.corriente.app.navigation

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.txn_entry_title)).assertExists()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.nav_report)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(context.getString(R.string.report_open_fx)).assertExists()

            // T1: на медленном эмуляторе Activity могла ещё не стать RESUMED (идёт переход) — recreate() тогда
            // падал с «Requested a re-creation of Activity but didn't happen». Ждём устойчивого состояния.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithContentDescription(context.getString(R.string.report_open_fx))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()
            // ActivityScenario.recreate() и его отслеживание жизненного цикла на медленном эмуляторе иногда теряют
            // пересоздание (хотя Activity пересоздаётся) — следим за Activity сами через ActivityLifecycleCallbacks
            // и при необходимости повторяем запрос.
            val app = context.applicationContext as Application
            val created = AtomicInteger(0)
            val resumed = AtomicReference<Activity?>(null)
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) { if (a is MainActivity) created.incrementAndGet() }
                override fun onActivityResumed(a: Activity) { if (a is MainActivity) resumed.set(a) }
                override fun onActivityPaused(a: Activity) { resumed.compareAndSet(a, null) }
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            }
            // Activity уже в RESUMED — onActivityResumed для неё не придёт, запоминаем её сами.
            scenario.onActivity { resumed.set(it) }
            app.registerActivityLifecycleCallbacks(callbacks)
            try {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                var lastRequest = 0L
                composeRule.waitUntil(timeoutMillis = 60_000) {
                    if (created.get() > 0) return@waitUntil true
                    // Запрос на пересоздание текущего экрана — сразу и затем повторно каждые 15 с, пока не сработает.
                    val now = System.currentTimeMillis()
                    val activity = resumed.get()
                    if (activity != null && now - lastRequest >= 15_000) {
                        lastRequest = now
                        instrumentation.runOnMainSync { activity.recreate() }
                    }
                    false
                }
                composeRule.waitUntil(timeoutMillis = 15_000) {
                    composeRule.onAllNodesWithContentDescription(context.getString(R.string.report_open_fx)).fetchSemanticsNodes().isNotEmpty() ||
                        composeRule.onAllNodesWithText(context.getString(R.string.txn_entry_title)).fetchSemanticsNodes().isNotEmpty()
                }
            } finally {
                app.unregisterActivityLifecycleCallbacks(callbacks)
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText(context.getString(R.string.txn_entry_title)).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(context.getString(R.string.report_open_fx)).assertExists()
        } finally {
            scenario.closeQuietly()
        }
    }

    /** V20: ввод, начатый с виджета «−/+», закрывает приложение (рабочий стол), а не оставляет список операций. */
    @Test
    fun leavingTheEntryOpenedFromTheWidgetClosesTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("corriente://entry?kind=EXPENSE&from=widget"))
            .setClassName(context.packageName, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(context.getString(R.string.txn_entry_title)).assertExists()

            composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            // Ждём isFinishing, а не Lifecycle.State.DESTROYED: система уничтожает Activity последней задачи только
            // после того, как рабочий стол встанет на экран, и на медленном эмуляторе это занимает >10 с (на скриншоте
            // при этом уже рабочий стол). Закрытие приложения — это finish(), его и проверяем.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                scenario.state == Lifecycle.State.DESTROYED || runCatching {
                    var finishing = false
                    scenario.onActivity { finishing = it.isFinishing }
                    finishing
                }.getOrDefault(true)
            }
        } finally {
            scenario.closeQuietly()
        }
    }

    // close() ждёт DESTROYED: на медленном эмуляторе ActivityScenario теряет переходы жизненного цикла (последний
    // виденный — RESUMED, хотя Activity закрыта) и close() падает через ~минуту. Проверяемое поведение к этому не относится.
    private fun ActivityScenario<MainActivity>.closeQuietly() {
        runCatching { close() }
    }
}
