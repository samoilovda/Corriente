package com.corriente.data.backup

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Приёмочный тест T1.9: реальный round-trip через Room —
 * экспорт → очистка → импорт → данные идентичны, включая UUID и архивные записи (I-21).
 */
class BackupRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var backup: BackupRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        backup = BackupRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun exportThenWipeThenRestoreYieldsIdenticalData() = runBlocking {
        db.currencyDao().insertAll(
            listOf(
                CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0),
                CurrencyEntity("USD", 2, 2, "$", isActive = true, displayOrder = 1),
            ),
        )
        val activeAccount = AccountEntity("a-1", "Наличные", "RUB", AccountKind.CASH, 100_00, 0)
        val archivedAccount = AccountEntity("a-2", "Старый вклад", "USD", AccountKind.SAVINGS, 0, 0, isArchived = true)
        db.accountDao().insert(activeAccount)
        db.accountDao().insert(archivedAccount)

        val activeCategory = CategoryEntity("c-1", "Еда", CategoryKind.EXPENSE, color = 1)
        val archivedCategory = CategoryEntity("c-2", "Хлам", CategoryKind.EXPENSE, color = 2, isArchived = true)
        db.categoryDao().insert(activeCategory)
        db.categoryDao().insert(archivedCategory)

        val txn = TxnEntity(
            id = "t-1", kind = TxnKind.EXPENSE, date = "2026-05-01", createdAt = 1, updatedAt = 1,
            accountId = "a-1", amountMinor = 42_00, currencyCode = "RUB", categoryId = "c-1", note = "обед",
        )
        db.txnDao().insert(txn)

        val exported = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

        db.txnDao().deleteAll()
        db.accountDao().deleteAll()
        db.categoryDao().deleteAll()
        db.currencyDao().deleteAll()
        assertEquals(emptyList<TxnEntity>(), db.txnDao().observeAll().first())

        backup.restore(ByteArrayInputStream(exported))

        assertEquals(2, db.currencyDao().observeAll().first().size)
        assertEquals(
            setOf(activeAccount, archivedAccount),
            (db.accountDao().observeActive().first() + db.accountDao().observeArchived().first()).toSet(),
        )
        assertEquals(
            setOf(activeCategory, archivedCategory),
            (db.categoryDao().observeActive().first() + db.categoryDao().observeArchived().first()).toSet(),
        )
        assertEquals(listOf(txn), db.txnDao().observeAll().first())
    }

    @Test
    fun restoreRejectsANewerSchemaVersion() = runBlocking {
        val fromFuture = """
            {"schemaVersion": 999, "exportedAt": 0, "currencies": [], "accounts": [], "categories": [],
             "transactions": [], "importBatches": [], "importAliases": [], "appSettings": []}
        """.trimIndent()
        val error = runCatching { backup.restore(ByteArrayInputStream(fromFuture.toByteArray())) }.exceptionOrNull()
        assert(error is BackupVersionException) { "expected BackupVersionException, got $error" }
    }
}
