package com.corriente.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/** T5.1: ротация бэкапов — держим последние N по хронологии (= лексикографии имён). */
class BackupRotationTest {

    private val files = listOf(
        "corriente-backup-20260101-000000.json",
        "corriente-backup-20260103-120000.json",
        "corriente-backup-20260102-090000.json",
        "corriente-backup-20260105-235959.json",
    )

    @Test
    fun `keeps the newest N and returns the rest for deletion`() {
        assertEquals(
            listOf(
                "corriente-backup-20260101-000000.json",
                "corriente-backup-20260102-090000.json",
            ),
            namesToPrune(files, keep = 2),
        )
    }

    @Test
    fun `nothing to prune when within limit`() {
        assertEquals(emptyList<String>(), namesToPrune(files, keep = 4))
        assertEquals(emptyList<String>(), namesToPrune(files, keep = 10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `keep must be positive`() {
        namesToPrune(files, keep = 0)
    }

    // F1.3 — при уменьшении retention лишние старые файлы уходят в подрезку.
    @Test
    fun `lowering retention prunes more of the oldest files`() {
        assertEquals(1, namesToPrune(files, keep = 3).size)
        assertEquals(2, namesToPrune(files, keep = 2).size)
        assertEquals(
            listOf(
                "corriente-backup-20260101-000000.json",
                "corriente-backup-20260102-090000.json",
                "corriente-backup-20260103-120000.json",
            ),
            namesToPrune(files, keep = 1),
        )
    }
}
