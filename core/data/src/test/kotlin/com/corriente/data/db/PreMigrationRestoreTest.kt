package com.corriente.data.db

import java.io.File
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A2.15: список копий и отложенное восстановление (на файлах, без Android). */
class PreMigrationRestoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun dbFile() = File(tmp.newFolder("databases"), "corriente.db").apply { writeText("live") }

    private fun copy(db: File, reason: String, stamp: String, text: String) {
        File(db.parentFile, "pre-migration").apply { mkdirs() }
            .resolve("corriente.db.$reason.$stamp").writeText(text)
    }

    @Test
    fun listsCopiesNewestFirst() {
        val db = dbFile()
        copy(db, "v5", "20260101-100000", "a")
        copy(db, "pre-restore", "20260202-100000", "bb")
        val list = PreMigrationBackup.listCopies(db)
        assertEquals(listOf("pre-restore", "v5"), list.map { it.reason })
        assertEquals(listOf("20260202-100000", "20260101-100000"), list.map { it.stamp })
        assertEquals(2L, list.first().sizeBytes)
    }

    @Test
    fun stageRejectsUnknownCopy() {
        val db = dbFile()
        assertFalse(PreMigrationBackup.stageRestore(db, "corriente.db.v1.20260101-100000"))
        assertFalse(PreMigrationBackup.applyPendingRestore(db))
    }

    @Test
    fun appliesStagedRestoreAndKeepsCurrentAsCopy() {
        val db = dbFile()
        copy(db, "v5", "20260101-100000", "old")
        File(db.path + "-wal").writeText("stale-wal")
        assertTrue(PreMigrationBackup.stageRestore(db, "corriente.db.v5.20260101-100000"))

        assertTrue(PreMigrationBackup.applyPendingRestore(db))

        assertEquals("old", db.readText())
        assertFalse(File(db.path + "-wal").exists())
        assertFalse(File(db.path + ".restoring").exists())
        val reasons = PreMigrationBackup.listCopies(db).associate { it.reason to it }
        assertTrue("pre-restore-copy" in reasons)
        val saved = File(db.parentFile, "pre-migration/" + reasons.getValue("pre-restore-copy").name)
        assertEquals("live", saved.readText())
        // метка снята — второй запуск ничего не делает
        assertFalse(PreMigrationBackup.applyPendingRestore(db))
    }

    @Test
    fun restoreSurvivesRotationOfTheChosenCopy() {
        val db = dbFile()
        // три копии = лимит; снимок текущей БД вытеснит самую старую — ту, что восстанавливаем
        copy(db, "v1", "20250101-100000", "oldest")
        copy(db, "v2", "20250202-100000", "mid")
        copy(db, "v3", "20250303-100000", "new")
        assertTrue(PreMigrationBackup.stageRestore(db, "corriente.db.v1.20250101-100000"))
        assertTrue(PreMigrationBackup.applyPendingRestore(db))
        assertEquals("oldest", db.readText())
    }
}
