package com.corriente.data.applock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5.2: чистая функция «нужно ли запрашивать разблокировку сейчас» — критерий приёмки. */
class AppLockPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `mode OFF never prompts`() {
        assertFalse(shouldPromptForUnlock(AppLockMode.OFF, lastUnlockAtMs = null, nowMs = now))
        assertFalse(shouldPromptForUnlock(AppLockMode.OFF, lastUnlockAtMs = now, nowMs = now))
    }

    @Test
    fun `mode EVERY_OPEN always prompts, even right after a successful unlock`() {
        assertTrue(shouldPromptForUnlock(AppLockMode.EVERY_OPEN, lastUnlockAtMs = null, nowMs = now))
        assertTrue(shouldPromptForUnlock(AppLockMode.EVERY_OPEN, lastUnlockAtMs = now, nowMs = now))
    }

    @Test
    fun `mode AFTER_5_MINUTES prompts when never unlocked before`() {
        assertTrue(shouldPromptForUnlock(AppLockMode.AFTER_5_MINUTES, lastUnlockAtMs = null, nowMs = now))
    }

    @Test
    fun `mode AFTER_5_MINUTES does not prompt right after unlocking`() {
        assertFalse(shouldPromptForUnlock(AppLockMode.AFTER_5_MINUTES, lastUnlockAtMs = now, nowMs = now))
        assertFalse(
            shouldPromptForUnlock(
                AppLockMode.AFTER_5_MINUTES,
                lastUnlockAtMs = now,
                nowMs = now + APP_LOCK_AFTER_MINUTES_THRESHOLD_MS - 1,
            ),
        )
    }

    @Test
    fun `mode AFTER_5_MINUTES prompts once the threshold is reached`() {
        assertTrue(
            shouldPromptForUnlock(
                AppLockMode.AFTER_5_MINUTES,
                lastUnlockAtMs = now,
                nowMs = now + APP_LOCK_AFTER_MINUTES_THRESHOLD_MS,
            ),
        )
        assertTrue(
            shouldPromptForUnlock(
                AppLockMode.AFTER_5_MINUTES,
                lastUnlockAtMs = now,
                nowMs = now + APP_LOCK_AFTER_MINUTES_THRESHOLD_MS + 60_000L,
            ),
        )
    }
}
