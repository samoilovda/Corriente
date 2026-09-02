package com.corriente.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWarningTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `recent backup does not warn`() {
        val lastRunAt = now - 1 * day
        assertFalse(shouldWarnAboutBackup(lastRunAt, enabled = true, txnCount = 100, now = now))
    }

    @Test
    fun `old backup warns`() {
        val lastRunAt = now - 20 * day
        assertTrue(shouldWarnAboutBackup(lastRunAt, enabled = true, txnCount = 100, now = now))
    }

    @Test
    fun `disabled with many txns warns`() {
        assertTrue(shouldWarnAboutBackup(lastRunAt = null, enabled = false, txnCount = 100, now = now))
        assertTrue(shouldWarnAboutBackup(lastRunAt = now - 1 * day, enabled = false, txnCount = 51, now = now))
    }

    @Test
    fun `disabled with few txns does not warn`() {
        assertFalse(shouldWarnAboutBackup(lastRunAt = null, enabled = false, txnCount = 10, now = now))
        assertFalse(shouldWarnAboutBackup(lastRunAt = now - 30 * day, enabled = false, txnCount = 50, now = now))
    }

    @Test
    fun `never run warns when enabled and above threshold`() {
        assertTrue(shouldWarnAboutBackup(lastRunAt = null, enabled = true, txnCount = 51, now = now))
    }

    @Test
    fun `boundary exactly 14 days does not warn yet`() {
        val lastRunAt = now - BACKUP_WARNING_THRESHOLD_MS
        assertFalse(shouldWarnAboutBackup(lastRunAt, enabled = true, txnCount = 100, now = now))
    }

    @Test
    fun `boundary just over 14 days warns`() {
        val lastRunAt = now - BACKUP_WARNING_THRESHOLD_MS - 1
        assertTrue(shouldWarnAboutBackup(lastRunAt, enabled = true, txnCount = 100, now = now))
    }
}
