package com.corriente.app.ui.recurring

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.data.repository.RecurrenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** R2.4 — ViewModel экрана «Повторяющиеся»: создание (оба типа правила), правка, удаление. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 9, 2)

    private class Fakes {
        val recurrenceDao = FakeRecurrenceDao()
        val accountDao = FakeAccountDao()
        val categoryDao = FakeCategoryDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
    }

    private suspend fun Fakes.seed() {
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        categoryDao.insert(CategoryEntity("rent", "Аренда", CategoryKind.EXPENSE, color = 0))
    }

    private fun vm(fakes: Fakes) = RecurringViewModel(
        recurrenceRepository = RecurrenceRepository(fakes.recurrenceDao),
        accountRepository = AccountRepository(fakes.accountDao),
        categoryRepository = CategoryRepository(fakes.categoryDao),
        currencyRepository = CurrencyRepository(fakes.currencyDao),
        today = { today },
    )

    private fun CoroutineScope.observe(model: RecurringViewModel) = launch { model.uiState.collect {} }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `creating a day-of-month rule adds it to the list`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setCategory("rent")
        model.setAmountText("5000")
        model.setRuleType(RecurrenceRuleType.DAY_OF_MONTH)
        model.setDayOfMonthText("1")
        model.save()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("Аренда", row.categoryName)
        assertEquals("5 000.00 ₽", row.amountText)
        assertEquals(RecurrenceRule.DayOfMonth(1), row.rule)
        assertEquals(null, model.uiState.value.editor)
    }

    @Test
    fun `creating an every-N-days income rule works`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setKind(TxnKind.INCOME)
        model.setAmountText("100")
        model.setRuleType(RecurrenceRuleType.EVERY_N_DAYS)
        model.setIntervalDaysText("14")
        model.save()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals(TxnKind.INCOME, row.kind)
        assertEquals(RecurrenceRule.EveryNDays(14), row.rule)
    }

    @Test
    fun `editing changes the amount and reschedules from today`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setAmountText("100")
        model.save()
        advanceUntilIdle()
        val id = model.uiState.value.rows.single().id

        model.startEdit(id)
        advanceUntilIdle()
        model.setAmountText("200")
        model.save()
        advanceUntilIdle()

        assertEquals("200.00 ₽", model.uiState.value.rows.single().amountText)
    }

    @Test
    fun `deleting removes the rule`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setAmountText("1")
        model.save()
        advanceUntilIdle()
        val id = model.uiState.value.rows.single().id

        model.delete(id)
        advanceUntilIdle()
        assertTrue(model.uiState.value.rows.isEmpty())
    }
}
