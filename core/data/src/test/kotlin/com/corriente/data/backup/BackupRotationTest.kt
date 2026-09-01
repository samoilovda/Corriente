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
}
