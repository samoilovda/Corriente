package com.corriente.app.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareBackupCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `deletes files older than max age and keeps fresh ones`() {
        val dir = tmp.newFolder("share")
        val now = 1_700_000_000_000L

        val old = File(dir, "old.json").apply { writeText("x"); setLastModified(now - ShareBackupCache.MAX_AGE_MS - 1) }
        val fresh = File(dir, "fresh.json").apply { writeText("x"); setLastModified(now - 1_000) }

        ShareBackupCache.cleanOldFiles(dir, now = now)

        assertFalse("старый файл должен быть удалён", old.exists())
        assertTrue("свежий файл должен остаться", fresh.exists())
    }

    @Test
    fun `exactly at threshold is not deleted`() {
        val dir = tmp.newFolder("share")
        val now = 1_700_000_000_000L
        val boundary = File(dir, "boundary.json").apply { writeText("x"); setLastModified(now - ShareBackupCache.MAX_AGE_MS) }

        ShareBackupCache.cleanOldFiles(dir, now = now)

        assertTrue("файл ровно на границе суток ещё не 'старше'", boundary.exists())
    }

    @Test
    fun `missing directory does not throw`() {
        ShareBackupCache.cleanOldFiles(File(tmp.root, "does-not-exist"))
    }
}
