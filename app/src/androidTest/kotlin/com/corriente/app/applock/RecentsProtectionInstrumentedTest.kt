package com.corriente.app.applock

import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A2.9: включённый замок скрывает содержимое в «Недавних». На API 33+ используется
 * `setRecentsScreenshotEnabled` (состояние окна наружу не читается — проверить можно только отсутствие сбоя и
 * симметрию), на API 26–32 — `FLAG_SECURE`, его и проверяем.
 */
@RunWith(AndroidJUnit4::class)
class RecentsProtectionInstrumentedTest {

    @Test
    fun securesTheWindowOnOldApisWhenLockedAndReleasesItWhenNot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                RecentsProtection.apply(activity, locked = true)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    assertEquals(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
                    )
                }
                RecentsProtection.apply(activity, locked = false)
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        assumeTrue("на API 33+ флаг окна не используется — проверено отсутствие сбоя", Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
    }
}
