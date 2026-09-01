package com.corriente.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

/** T5.1 / I-20: копия файла БД создаётся только когда файл старее целевой версии схемы. */
@RunWith(AndroidJUnit4::class)
class PreMigrationBackupInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "premig_test.db"

    private fun dbFile() = context.getDatabasePath(dbName)
    private fun backupDir() = File(dbFile().parentFile, "pre-migration")

    @Before
    fun clean() {
        dbFile().parentFile?.mkdirs()
        SQLiteDatabase.deleteDatabase(dbFile())
        backupDir().deleteRecursively()
    }

    private fun createDbAtVersion(version: Int) {
        SQLiteDatabase.openOrCreateDatabase(dbFile(), null).use { it.version = version }
    }

    @Test
    fun copiesFileWhenOlderThanTarget() {
        createDbAtVersion(1)

        val copy = PreMigrationBackup.runIfNeeded(context, dbName, targetVersion = 2, now = Date(0))
        assertNotNull(copy)
        assertEquals(1, backupDir().listFiles { f -> f.name.startsWith("$dbName.v1.") }?.size)
    }

    @Test
    fun doesNothingWhenAlreadyAtTargetVersion() {
        createDbAtVersion(2)

        val copy = PreMigrationBackup.runIfNeeded(context, dbName, targetVersion = 2)
        assertNull(copy)
        assertEquals(false, backupDir().exists() && backupDir().listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun doesNothingWhenNoDbFile() {
        assertNull(PreMigrationBackup.runIfNeeded(context, dbName, targetVersion = 2))
    }
}
