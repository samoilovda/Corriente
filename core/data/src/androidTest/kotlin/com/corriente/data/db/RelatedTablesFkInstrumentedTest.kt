package com.corriente.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.BudgetEntity
import com.corriente.data.db.entity.BudgetPeriod
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.RecurrenceEntity
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.imports.MonefyCsvParser
import com.corriente.data.imports.MonefyImportPlanner
import com.corriente.data.imports.MonefyImportRepository
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.DeleteBlocker
import com.corriente.data.repository.DeleteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A1.7: FK `budget`/`recurrence` в путях удаления, слияния и отката импорта — на настоящем Room. */
class RelatedTablesFkInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        categories = CategoryRepository(db.categoryDao())
        accounts = AccountRepository(db.accountDao())
        runBlocking {
            db.currencyDao().insertAll(
                listOf(
                    CurrencyEntity("RUB", 2, 2, "₽", isActive = true),
                    CurrencyEntity("USD", 2, 2, "$", isActive = true),
                ),
            )
            db.accountDao().insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
            db.categoryDao().insert(CategoryEntity("food", "Еда", CategoryKind.EXPENSE, color = 0))
            db.categoryDao().insert(CategoryEntity("groc", "Продукты", CategoryKind.EXPENSE, color = 0))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun budget(id: String, categoryId: String?, currency: String = "RUB", amount: Long = 1_000_00) =
        BudgetEntity(id, categoryId, currency, amount, BudgetPeriod.MONTH, "2026-01-01")

    private fun rule(id: String, categoryId: String?) = RecurrenceEntity(
        id = id, kind = TxnKind.EXPENSE, accountId = "cash", categoryId = categoryId,
        amountMinor = 100, currencyCode = "RUB", note = null,
        ruleType = RecurrenceRuleType.EVERY_N_DAYS, dayOfMonth = null, intervalDays = 7,
        nextRunOn = "2026-09-01", lastCreatedTxnId = null,
    )

    @Test
    fun deletingACategoryWithABudgetOrRuleIsRefusedWithReasons() = runBlocking {
        db.budgetDao().insert(budget("b", "food"))
        db.recurrenceDao().insert(rule("r", "food"))

        assertEquals(
            DeleteResult.Blocked(setOf(DeleteBlocker.BUDGETS, DeleteBlocker.RECURRENCES)),
            categories.deleteIfUnused("food"),
        )
        assertNotNull(db.categoryDao().getById("food"))
    }

    @Test
    fun deletingAnAccountWithARuleIsRefused() = runBlocking {
        db.recurrenceDao().insert(rule("r", null))
        assertEquals(DeleteResult.Blocked(setOf(DeleteBlocker.RECURRENCES)), accounts.deleteIfUnused("cash"))
    }

    @Test
    fun mergeMovesBudgetsAndRulesToTheTarget() = runBlocking {
        db.budgetDao().insert(budget("b-food", "food", "RUB", 5_000_00))
        db.budgetDao().insert(budget("b-usd", "food", "USD", 100_00))
        db.budgetDao().insert(budget("b-groc", "groc", "RUB", 7_000_00)) // конфликт по (категория, валюта)
        db.recurrenceDao().insert(rule("r", "food"))

        categories.mergeInto("food", "groc")

        assertNull(db.categoryDao().getById("food"))
        val budgets = db.budgetDao().getAll().associateBy { it.id }
        assertNull(budgets["b-food"]) // остаётся бюджет целевой категории
        assertEquals("groc", budgets.getValue("b-usd").categoryId)
        assertEquals(7_000_00, budgets.getValue("b-groc").amountMinor)
        assertEquals("groc", db.recurrenceDao().getById("r")!!.categoryId)
    }

    @Test
    fun rollbackSucceedsWhenTheUserBuiltOnAnImportedCategory() = runBlocking {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!.readBytes().toString(Charsets.UTF_8)
        val plan = MonefyImportPlanner.plan(MonefyCsvParser.parse(csv))
        val repo = MonefyImportRepository(db)
        val result = repo.import(plan, "monefy_sample.csv")

        val imported = db.categoryDao().observeAll().first().filter { it.origin == CategoryOrigin.IMPORT }
        val withBudget = imported.first()
        val withRule = imported.last { it.id != withBudget.id }
        val parent = imported.first { it.id != withBudget.id && it.id != withRule.id }
        db.budgetDao().insert(budget("b", withBudget.id))
        db.recurrenceDao().insert(rule("r", withRule.id))
        db.categoryDao().insert(
            CategoryEntity("child", "Моя подкатегория", parent.kind, parentId = parent.id, color = 0),
        )

        repo.rollback(result.batchId)

        assertTrue(db.txnDao().observeAll().first().isEmpty())
        val left = db.categoryDao().observeAll().first().map { it.id }.toSet()
        assertTrue(withBudget.id in left && withRule.id in left && parent.id in left)
        assertTrue(db.importBatchDao().getAll().none { it.id == result.batchId })
    }
}
