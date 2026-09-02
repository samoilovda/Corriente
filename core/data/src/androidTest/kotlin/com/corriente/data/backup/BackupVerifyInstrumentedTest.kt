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
import java.io.ByteArrayOutputStream

/**
 * [БД] R1.4: «Проверить файл» — восстановление во временную in-memory БД, реальные данные
 * не трогаются. Тот же приём, что уже применён в BackupRepositoryInstrumentedTest для round-trip,
 * только сюда добавлена вторая, отдельная временная БД и утверждение, что основная не менялась.
 */
class BackupVerifyInstrumentedTest {

    private lateinit var mainDb: AppDatabase
    private lateinit var mainBackup: BackupRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mainDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        mainBackup = BackupRepository(mainDb)
    }

    @After
    fun tearDown() = mainDb.close()

    @Test
    fun verifyingAFileRestoresOnlyIntoATempDbAndLeavesRealDataUntouched() = runBlocking {
        // "Реальные" данные — то, что ни в коем случае не должно измениться.
        mainDb.currencyDao().insertAll(listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true, displayOrder = 0)))
        mainDb.accountDao().insert(AccountEntity("real-acc", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        mainDb.categoryDao().insert(CategoryEntity("real-cat", "Еда", CategoryKind.EXPENSE, color = 1))
        mainDb.txnDao().insert(
            TxnEntity(
                id = "real-t", kind = TxnKind.EXPENSE, date = "2026-01-01", createdAt = 0, updatedAt = 0,
                accountId = "real-acc", amountMinor = 500, currencyCode = "RUB", categoryId = "real-cat",
            ),
        )
        val realSummaryBefore = mainBackup.currentSummary()

        // Файл, который "проверяют" — другой набор данных, не связанный с текущей БД.
        val fileDb = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val fileBackup = BackupRepository(fileDb)
        fileDb.currencyDao().insertAll(listOf(CurrencyEntity("USD", 2, 2, "$", isActive = true, displayOrder = 0)))
        fileDb.accountDao().insert(AccountEntity("file-acc", "Card", "USD", AccountKind.CARD, 0, 0))
        fileDb.categoryDao().insert(CategoryEntity("file-cat", "Транспорт", CategoryKind.EXPENSE, color = 2))
        fileDb.txnDao().insertAll(
            listOf(
                TxnEntity(
                    id = "file-t1", kind = TxnKind.INCOME, date = "2026-02-01", createdAt = 0, updatedAt = 0,
                    accountId = "file-acc", amountMinor = 10_000, currencyCode = "USD", categoryId = "file-cat",
                ),
                TxnEntity(
                    id = "file-t2", kind = TxnKind.EXPENSE, date = "2026-02-02", createdAt = 0, updatedAt = 0,
                    accountId = "file-acc", amountMinor = 3_000, currencyCode = "USD", categoryId = "file-cat",
                ),
            ),
        )
        val exported = ByteArrayOutputStream().also { fileBackup.export(it) }.toByteArray()
        fileDb.close()

        // Проверка: разбор + restorePayload на СВЕЖЕЙ временной БД, а не на mainDb.
        val payload = BackupRepository.parsePayload(exported.inputStream())
        val tempDb = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val tempBackup = BackupRepository(tempDb)
            tempBackup.restorePayload(payload)
            val tempSummary = tempBackup.currentSummary()

            assertEquals(1, tempSummary.accounts)
            assertEquals(1, tempSummary.categories)
            assertEquals(2, tempSummary.transactions)
            assertEquals(mapOf("USD" to 7_000L), tempSummary.sumsByCurrency)
        } finally {
            tempDb.close()
        }

        // Реальные данные не тронуты — ни на уровне сводки, ни на уровне сырых строк.
        assertEquals(realSummaryBefore, mainBackup.currentSummary())
        assertEquals(1, mainDb.accountDao().observeActive().first().size)
        assertEquals("real-acc", mainDb.txnDao().observeAll().first().single().accountId)
    }
}
