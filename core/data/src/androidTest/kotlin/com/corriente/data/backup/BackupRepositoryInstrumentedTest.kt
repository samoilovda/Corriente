package com.corriente.data.backup

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.ImportBatchEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.toDomain
import com.corriente.data.repository.toDomain
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

    // F1.4 — файл с висячей ссылкой на счёт: текущие данные не тронуты, причина понятна.
    // Регрессия: restorePayload вставляла txn ДО import_batch, а txn.import_batch_id — настоящий
    // Room @ForeignKey на import_batch(id) (TxnEntity.kt) — SQLite ронял вставку constraint'ом
    // на любом бэкапе с хотя бы одной импортированной операцией.
    @Test
    fun restoreRoundTripsATransactionLinkedToAnImportBatch() = runBlocking {
        db.currencyDao().insertAll(listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0)))
        db.accountDao().insert(AccountEntity("a-1", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        val batch = ImportBatchEntity("b-1", "monefy", "export.csv", importedAt = 1, rowCount = 1, reportJson = "{}")
        db.importBatchDao().insert(batch)
        val txn = TxnEntity(
            id = "t-imported", kind = TxnKind.EXPENSE, date = "2026-05-01", createdAt = 1, updatedAt = 1,
            accountId = "a-1", amountMinor = 10_00, currencyCode = "RUB", importBatchId = "b-1",
        )
        db.txnDao().insert(txn)

        val exported = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

        db.txnDao().deleteAll()
        db.importBatchDao().deleteAll()
        db.accountDao().deleteAll()
        db.currencyDao().deleteAll()

        backup.restore(ByteArrayInputStream(exported))

        assertEquals(listOf(txn), db.txnDao().observeAll().first())
        assertEquals(listOf(batch), db.importBatchDao().getAll())
    }

    @Test
    fun restoreRejectsAnInvalidPayloadWithoutTouchingCurrentData() = runBlocking {
        db.currencyDao().insertAll(listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0)))
        db.accountDao().insert(AccountEntity("keep", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        db.txnDao().insert(
            TxnEntity(
                id = "keep-t", kind = TxnKind.EXPENSE, date = "2026-01-01", createdAt = 0, updatedAt = 0,
                accountId = "keep", amountMinor = 100, currencyCode = "RUB",
            ),
        )

        val broken = """
            {"schemaVersion": 1, "exportedAt": 0,
             "currencies": [{"code":"RUB","minorUnits":2,"displayScale":2,"symbol":"₽","isActive":true,"displayOrder":0}],
             "accounts": [],
             "categories": [],
             "transactions": [{"id":"x","kind":"EXPENSE","date":"2026-01-01","createdAt":0,"updatedAt":0,
               "accountId":"ghost","amountMinor":100,"currencyCode":"RUB","toAccountId":null,"toAmountMinor":null,
               "toCurrencyCode":null,"categoryId":null,"note":null,"importBatchId":null,"importHash":null}],
             "importBatches": [], "importAliases": [], "appSettings": []}
        """.trimIndent()

        val error = runCatching { backup.restore(ByteArrayInputStream(broken.toByteArray())) }.exceptionOrNull()
        assert(error is BackupInvalidException) { "expected BackupInvalidException, got $error" }
        assertEquals(1, db.txnDao().observeAll().first().size) // прежние данные на месте
        assertEquals(1, db.accountDao().observeActive().first().size)
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

    // A1.12: свойство — если validate не нашёл проблем, restorePayload проходит, и всё читается доменной
    // моделью; иначе restorePayload отказывает ДО записи и текущая БД не меняется.
    @Test
    fun validateEmptyImpliesRestoreSucceedsAndEverythingReadable() = runBlocking {
        val base = BackupPayload(
            schemaVersion = AppDatabase.SCHEMA_VERSION, exportedAt = 0,
            currencies = listOf(CurrencyBackup("RUB", 2, 2, "₽", true, 0), CurrencyBackup("USD", 2, 2, "$", true, 1)),
            accounts = listOf(
                AccountBackup("a", "Нал", "RUB", "CASH", 0, 0, null, 0, false, true),
                AccountBackup("b", "Карта", "USD", "CARD", 0, 0, null, 0, false, true),
            ),
            categories = listOf(CategoryBackup("c", "Еда", "EXPENSE", null, 0, null, "USER", 0, false, null)),
            transactions = listOf(
                TxnBackup("t1", "EXPENSE", "2026-01-01", 0, 0, "a", 100, "RUB", null, null, null, "c", null, null, "h1"),
                TxnBackup("t2", "TRANSFER", "2026-01-02", 0, 0, "a", 100, "RUB", "b", 2, "USD", null, null, null, null),
            ),
            importBatches = emptyList(), importAliases = emptyList(), appSettings = emptyList(),
        )
        val mutations: List<(BackupPayload) -> BackupPayload> = listOf(
            { it },
            { it.copy(currencies = it.currencies.map { c -> if (c.code == "RUB") c.copy(code = "rub") else c }) },
            { it.copy(currencies = it.currencies.map { c -> if (c.code == "RUB") c.copy(minorUnits = 5) else c }) },
            { it.copy(currencies = it.currencies.map { c -> if (c.code == "RUB") c.copy(displayScale = 3) else c }) },
            { it.copy(accounts = it.accounts.map { a -> if (a.id == "a") a.copy(kind = "WALLET") else a }) },
            { it.copy(accounts = it.accounts + it.accounts.first()) },
            { it.copy(categories = it.categories.map { c -> c.copy(origin = "ROBOT") }) },
            { it.copy(categories = it.categories + it.categories.first().copy(id = "c2")) }, // тот же (name, kind)
            { it.copy(transactions = it.transactions.map { t -> if (t.id == "t1") t.copy(currencyCode = "USD") else t }) },
            { it.copy(transactions = it.transactions.map { t -> if (t.id == "t2") t.copy(toCurrencyCode = "RUB") else t }) },
            { it.copy(transactions = it.transactions.map { t -> if (t.id == "t2") t.copy(toAccountId = "a") else t }) },
            { it.copy(transactions = it.transactions.map { t -> t.copy(importHash = "same") }) },
            { it.copy(transactions = it.transactions + it.transactions.first()) },
        )
        db.currencyDao().insertAll(listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true)))
        db.accountDao().insert(AccountEntity("keep", "Прежний", "RUB", AccountKind.CASH, 0, 0))

        var restored = 0
        mutations.forEach { mutate ->
            val payload = mutate(base)
            if (BackupRepository.validate(payload).isEmpty()) {
                backup.restorePayload(payload)
                db.currencyDao().observeAll().first().forEach { it.toDomain() }
                db.accountDao().observeAll().first().forEach { it.toDomain() }
                db.categoryDao().observeAll().first().forEach { it.toDomain() }
                db.txnDao().observeAll().first().forEach { it.toDomain() }
                restored++
            } else {
                val error = runCatching { backup.restorePayload(payload) }.exceptionOrNull()
                assert(error is BackupInvalidException) { "expected BackupInvalidException, got $error" }
            }
        }
        assertEquals(1, restored) // только неизменённый файл
    }
}
