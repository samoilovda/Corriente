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
        assertEquals("Еда", row.categoryName)
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

        assertEquals(null, model.uiState.value.rows.single().categoryName)
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

    // A2.3: пустая/нулевая сумма — редактор остаётся открытым, сообщение, в БД ничего.
    @Test
    fun `saving with an empty or zero amount keeps the editor open with a message`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        backgroundScope.launch { model.messages.collect {} }
        advanceUntilIdle()

        model.startCreate()
        advanceUntilIdle()
        assertEquals(false, model.uiState.value.editor!!.canSave)
        model.save()
        advanceUntilIdle()
        assertEquals(com.corriente.app.R.string.budgets_error_invalid_amount, model.messages.value?.resId)
        assertEquals(false, model.uiState.value.editor!!.saving)

        model.setAmountText("0")
        model.save()
        advanceUntilIdle()

        assertEquals(true, model.uiState.value.rows.isEmpty())
        assertEquals(true, model.uiState.value.editor != null)
    }

    // A2.4: бюджет на архивную категорию подписан её именем, а не «На всё».
    @Test
    fun `a budget on an archived category keeps the category name and is flagged archived`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        model.startCreate(); model.setCategory("food"); model.setAmountText("100"); model.save()
        advanceUntilIdle()

        CategoryRepository(fakes.categoryDao).archive("food")
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("Еда", row.categoryName)
        assertEquals(true, row.categoryArchived)
        assertEquals(true, model.uiState.value.expenseCategories.isEmpty()) // выбрать её для нового бюджета нельзя
    }

    // A2.4: второй бюджет на ту же пару (категория, валюта) отвергается с сообщением.
    @Test
    fun `a second budget for the same category and currency is rejected`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        backgroundScope.launch { model.messages.collect {} }
        advanceUntilIdle()
        model.startCreate(); model.setCategory("food"); model.setAmountText("100"); model.save()
        advanceUntilIdle()

        model.startCreate(); model.setCategory("food"); model.setAmountText("200"); model.save()
        advanceUntilIdle()

        assertEquals(1, model.uiState.value.rows.size)
        assertEquals(com.corriente.app.R.string.budgets_error_duplicate, model.messages.value?.resId)
    }

    // A2.4: правка бюджета не сдвигает его начало на «сегодня».
    @Test
    fun `editing a budget keeps its original start date`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        fakes.budgetDao.insert(
            com.corriente.data.db.entity.BudgetEntity("b", "food", "RUB", 10_000, com.corriente.data.db.entity.BudgetPeriod.MONTH, "2026-01-01"),
        )
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.startEdit("b"); advanceUntilIdle()
        model.setAmountText("250"); model.save(); advanceUntilIdle()

        assertEquals("2026-01-01", fakes.budgetDao.getById("b")!!.startsOn)
    }
}
