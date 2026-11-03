package com.corriente.data.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.repository.AccountRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** W1: база десктопа прошлой версии схемы открывается миграциями, а не падает; перед миграцией — копия файла (I-20). */
class DesktopMigrationTest {

    @get:Rule val tmp = TemporaryFolder()
    private val rub = CurrencyCode("RUB")

    @Test
    fun migrationsCoverEveryVersionFromEightToCurrent() {
        val versions = DESKTOP_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((8 until AppDatabase.SCHEMA_VERSION).map { it to it + 1 }, versions)
    }

    @Test
    fun schemaEightFileOpensWithDataAndBackupCopy() = runBlocking {
        val file = tmp.newFile("corriente.db").also { it.delete() }
        DesktopDatabase.open(file).apply {
            AccountRepository(accountDao()).create("Нал", rub, AccountKind.CASH, Money(Minor(100_00), rub), 0)
            close()
        }
        // Файл схемы 8 (8 → 9 структуру не меняла — достаточно версии).
        BundledSQLiteDriver().open(file.absolutePath).use { it.execSQL("PRAGMA user_version = 8") }

        val db = DesktopDatabase.open(file)
        val accounts = db.accountDao().observeAll().first()
        assertEquals(listOf("Нал"), accounts.map { it.name })
        db.close()

        val copies = java.io.File(file.parentFile, "backups").listFiles().orEmpty().filter { it.name.startsWith("pre-migration-v8-") }
        assertEquals(1, copies.size)
        BundledSQLiteDriver().open(copies.single().absolutePath).use { c ->
            c.prepare("SELECT COUNT(*) FROM account").use { st -> assertTrue(st.step()); assertEquals(1L, st.getLong(0)) }
        }
    }

    @Test
    fun currentVersionAndNewFileAreNotCopied() {
        val fresh = tmp.newFile("fresh.db").also { it.delete() }
        assertNull(DesktopDatabase.copyBeforeMigration(fresh))
        DesktopDatabase.open(fresh).close()
        assertNull(DesktopDatabase.copyBeforeMigration(fresh))
        BundledSQLiteDriver().open(fresh.absolutePath).use { it.execSQL("PRAGMA user_version = 8") }
        assertNotNull(DesktopDatabase.copyBeforeMigration(fresh))
    }
}
