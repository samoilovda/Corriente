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

    // A2.15: копия делается после checkpoint WAL — основной файл копии содержит последнюю запись и без своего -wal.
    @Test
    fun copyContainsTheLatestWriteEvenWithoutItsWalFile() {
        val live = SQLiteDatabase.openOrCreateDatabase(dbFile(), null)
        try {
            live.enableWriteAheadLogging()
            live.execSQL("CREATE TABLE t(v TEXT)")
            live.execSQL("INSERT INTO t VALUES ('last')") // остаётся в -wal, пока соединение открыто

            val copy = PreMigrationBackup.copyDatabaseFile(context, dbName, "pre-restore", Date(0))!!
            File(copy.path + "-wal").delete()
            File(copy.path + "-shm").delete()

            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT v FROM t", null).use { c ->
                    assertEquals(true, c.moveToFirst())
                    assertEquals("last", c.getString(0))
                }
            }
        } finally {
            live.close()
        }
    }
}
