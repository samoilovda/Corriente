package com.corriente.data.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * S1: триггеры захвата изменений на настоящем SQLite — каждая запись через репозитории попадает в `sync_outbox`,
 * а при `applying = 1`, выключенной синхронизации и для привязанных к устройству настроек — нет.
 */
class SyncCaptureInstrumentedTest {

    private lateinit var db: AppDatabase
    private val rub = CurrencyCode("RUB")

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(AppDatabase.seedCallback())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun outbox() = db.syncDao().outbox().map { it.tableName to it.pk }.toSet()

    private suspend fun clearOutbox() = db.syncDao().outbox().forEach { db.syncDao().removeFromOutbox(it.tableName, it.pk) }

    @Test
    fun nothingIsCapturedUntilSyncIsEnabled() = runBlocking {
        AccountRepository(db.accountDao()).create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0, null)
        assertTrue(outbox().isEmpty())
    }

    @Test
    fun repositoryWritesAreCapturedAndCoalesced() = runBlocking {
        db.syncDao().setState(SyncStateEntity(enabled = true))
        val accounts = AccountRepository(db.accountDao())
        val txns = TxnRepository(db.txnDao(), db.accountDao())
        val account = accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0, null)
        val txn = txns.addExpense(account.id, Money(Minor(100), rub), null, LocalDate.of(2026, 10, 1))
        txns.deleteById(txn.id)

        // создание + удаление одной операции — одна запись (содержимое берётся при выгрузке)
        assertEquals(setOf("account" to account.id, "txn" to txn.id), outbox())
    }

    @Test
    fun applyingRemoteChangesIsNotCapturedBack() = runBlocking {
        db.syncDao().setState(SyncStateEntity(enabled = true, applying = true))
        AccountRepository(db.accountDao()).create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0, null)
        assertTrue(outbox().isEmpty())
    }

    @Test
    fun deviceBoundSettingsStayLocal() = runBlocking {
        db.syncDao().setState(SyncStateEntity(enabled = true))
        db.appSettingDao().set(AppSettingEntity("app_lock.mode", "ALWAYS"))
        db.appSettingDao().set(AppSettingEntity("sync.device_id", "x"))
        db.appSettingDao().set(AppSettingEntity("auto_backup.tree_uri", "content://x"))
        db.appSettingDao().set(AppSettingEntity("overview.categories", "a,b"))
        assertEquals(setOf("app_setting" to "overview.categories"), outbox())
    }

    @Test
    fun categoryMergeCapturesEveryTouchedRow() = runBlocking {
        val accounts = AccountRepository(db.accountDao())
        val categories = CategoryRepository(db.categoryDao())
        val txns = TxnRepository(db.txnDao(), db.accountDao())
        val account = accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0, null)
        val from = categories.create("Такси", CategoryKind.EXPENSE, null, 0, null)
        val into = categories.create("Транспорт2", CategoryKind.EXPENSE, null, 0, null)
        val txn = txns.addExpense(account.id, Money(Minor(100), rub), from.id, LocalDate.of(2026, 10, 1))

        db.syncDao().setState(SyncStateEntity(enabled = true))
        clearOutbox()
        categories.mergeInto(from.id, into.id)

        val captured = outbox()
        assertTrue(captured.toString(), ("txn" to txn.id) in captured)
        assertTrue(captured.toString(), ("category" to from.id) in captured)
    }
}
