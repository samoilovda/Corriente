package com.corriente.data.imports

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T3.4: запись батча импорта в Room, идемпотентность повторного импорта, откат.
 * План берётся из синтетического файла (тот же, что в MonefyImportPlannerTest).
 */
class MonefyImportRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MonefyImportRepository

    private val plan by lazy {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        MonefyImportPlanner.plan(MonefyCsvParser.parse(csv))
    }

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = MonefyImportRepository(db)
        runBlocking {
            db.currencyDao().insertAll(
                listOf(
                    CurrencyEntity("RUB", 2, 2, "₽", isActive = true),
                    CurrencyEntity("USD", 2, 2, "$", isActive = true),
                ),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importsWholePlanThenSecondImportAddsNothingThenRollbackRestoresState() = runBlocking {
        val expectedRows = plan.plainTxns.size + plan.transfers.size // 7 + 5

        val first = repo.import(plan, "monefy_sample.csv")
        assertEquals(expectedRows, first.inserted)
        assertEquals(0, first.skipped)

        assertEquals(4, db.accountDao().observeActive().first().size)         // Cash, Card $, Savings, Wallet
        val importCategories = db.categoryDao().observeAll().first().filter { it.origin == CategoryOrigin.IMPORT }
        assertEquals(6, importCategories.size)                               // 5 реальных + [перевод без пары]
        assertEquals(expectedRows, db.txnDao().observeAll().first().size)

        // повторный импорт того же файла — 0 новых строк (I-19)
        val second = repo.import(plan, "monefy_sample.csv")
        assertEquals(0, second.inserted)
        assertEquals(expectedRows, second.skipped)
        assertEquals(expectedRows, db.txnDao().observeAll().first().size)

        // откат первого батча (второй ничего не вставил): БД возвращается в исходное состояние
        repo.rollback(first.batchId)
        assertTrue(db.txnDao().observeAll().first().isEmpty())
        assertTrue(
            db.categoryDao().observeAll().first().none { it.origin == CategoryOrigin.IMPORT },
        ) // осиротевшие IMPORT-категории удалены
        assertTrue(db.importBatchDao().getAll().none { it.id == first.batchId })
    }

    // F0.5 — счёт-тёзка в другой валюте: без решения импорт отклонён, с «отдельным счётом» — новый счёт.
    @Test
    fun existingAccountCurrencyMismatchBlocksImportUntilResolved() = runBlocking {
        // В приложении есть Cash/USD; в файле Cash/RUB.
        db.accountDao().insert(
            AccountEntity(id = "cash-usd", name = "Cash", currencyCode = "USD", kind = AccountKind.CASH, color = 0),
        )
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        val plan = MonefyImportPlanner.plan(
            MonefyCsvParser.parse(csv),
            existingAccounts = repo.existingAccountCurrencies(),
        )
        val ref = plan.reviews.single { it.reason == ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH }.ref()

        // без решения — отказ, ни одной строки
        val failure = runCatching { repo.import(plan, "monefy_sample.csv") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(db.txnDao().observeAll().first().isEmpty())

        // решение «отдельный счёт» — импорт проходит, появляется отдельный RUB-счёт
        repo.import(plan.applyReviewDecisions(mapOf(ref to ReviewDecision.SeparateAccount)), "monefy_sample.csv")
        val accounts = db.accountDao().observeAll().first()
        assertTrue(accounts.any { it.name == "Cash" && it.currencyCode == "USD" })
        assertTrue(accounts.any { it.name == "Cash (RUB)" && it.currencyCode == "RUB" })
        // у каждой операции валюта совпадает с валютой её счёта
        val curByAcc = accounts.associate { it.id to it.currencyCode }
        assertTrue(db.txnDao().observeAll().first().all { it.currencyCode == curByAcc[it.accountId] })
    }
}
