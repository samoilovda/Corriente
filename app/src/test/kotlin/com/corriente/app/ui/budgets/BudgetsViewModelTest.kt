package com.corriente.app.ui.budgets

import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.repository.BudgetRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
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

/** R2.3 — ViewModel экрана «Бюджеты»: создание, редактирование, удаление. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 9, 2)

    private class Fakes {
        val budgetDao = FakeBudgetDao()
        val categoryDao = FakeCategoryDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
    }

    private suspend fun Fakes.seed() {
        categoryDao.insert(CategoryEntity("food", "Еда", CategoryKind.EXPENSE, color = 0))
    }

    private fun vm(fakes: Fakes) = BudgetsViewModel(
        budgetRepository = BudgetRepository(fakes.budgetDao),
        categoryRepository = CategoryRepository(fakes.categoryDao),
        currencyRepository = CurrencyRepository(fakes.currencyDao),
        today = { today },
    )

    private fun CoroutineScope.observe(model: BudgetsViewModel) = launch { model.uiState.collect {} }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `creating a budget adds it to the list, formatted`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setCategory("food")
        model.setAmountText("100")
        model.save()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("Еда", row.categoryLabel)
        assertEquals("100.00 ₽", row.amountText)
        assertEquals(null, model.uiState.value.editor)
    }

    @Test
    fun `a null category means a whole-currency budget`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setAmountText("500")
        model.save()
        advanceUntilIdle()

        assertEquals("На всё", model.uiState.value.rows.single().categoryLabel)
    }

    @Test
    fun `editing loads the existing budget into the form and saves the change`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setCategory("food")
        model.setAmountText("100")
        model.save()
        advanceUntilIdle()

        val id = model.uiState.value.rows.single().id
        model.startEdit(id)
        advanceUntilIdle()
        assertEquals("food", model.uiState.value.editor?.categoryId)
        assertEquals("100.00", model.uiState.value.editor?.amountText)

        model.setAmountText("200")
        model.save()
        advanceUntilIdle()

        assertEquals("200.00 ₽", model.uiState.value.rows.single().amountText)
        assertEquals(null, model.uiState.value.editor)
    }

    @Test
    fun `deleting removes the budget from the list`() = runTest(dispatcher) {
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

    @Test
    fun `cancelling the editor discards unsaved changes`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startCreate()
        model.setAmountText("100")
        model.cancelEdit()
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.editor)
        assertTrue(model.uiState.value.rows.isEmpty())
    }
}
