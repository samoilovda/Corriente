package com.corriente.app.ui.txnentry

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TxnEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 15)

    private class Fakes {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(
            listOf(
                CurrencyEntity("RUB", 2, 2, "₽", true, 0),
                CurrencyEntity("CLP", 0, 0, "$", true, 1),
            ),
        )
        val categoryDao = FakeCategoryDao()
    }

    private suspend fun Fakes.seed() {
        accountDao.insert(entityAccount("acc-rub", "Наличные", "RUB"))
        accountDao.insert(entityAccount("acc-clp", "Песо", "CLP"))
        categoryDao.insert(entityCategory("cat-food", "Еда", CategoryKind.EXPENSE))
        categoryDao.insert(entityCategory("cat-salary", "Зарплата", CategoryKind.INCOME))
    }

    private fun entityAccount(id: String, name: String, code: String) = AccountEntity(
        id = id, name = name, currencyCode = code, kind = AccountKind.CASH,
        openingBalanceMinor = 0, color = 0,
    )

    private fun entityCategory(id: String, name: String, kind: CategoryKind) = CategoryEntity(
        id = id, name = name, kind = kind, color = 0,
    )

    private fun vm(
        fakes: Fakes,
        editingTxnId: String? = null,
        initialKind: EntryKind = EntryKind.EXPENSE,
    ): TxnEntryViewModel = TxnEntryViewModel(
        txns = TxnRepository(fakes.txnDao, fakes.accountDao),
        accounts = AccountRepository(fakes.accountDao),
        categories = CategoryRepository(fakes.categoryDao),
        currencies = CurrencyRepository(fakes.currencyDao),
        editingTxnId = editingTxnId,
        initialKind = initialKind,
        today = { today },
    )

    private fun CoroutineScope.observe(model: TxnEntryViewModel) {
        launch { model.uiState.collect {} }
        launch { model.finished.collect {} }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initialKind INCOME opens the form on the income branch`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes, initialKind = EntryKind.INCOME)
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertEquals(EntryKind.INCOME, model.uiState.value.kind)
        assertEquals(listOf("Зарплата"), model.uiState.value.categories.map { it.name })
    }

    @Test
    fun `first account is selected by default and its currency drives the keypad`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertEquals("acc-rub", model.uiState.value.selectedAccountId)
        assertEquals("RUB", model.uiState.value.currency?.code?.code)
    }

    @Test
    fun `cannot save with an empty amount, can once digits are entered`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertFalse(model.uiState.value.canSave)
        assertFalse(model.save())

        model.pressDigit('5'); model.pressDigit('0')
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)
    }

    @Test
    fun `calculator adds two amounts before saving`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        // 12.50 + 3.20 = 15.70
        model.pressDigit('1'); model.pressDigit('2'); model.pressDecimalPoint(); model.pressDigit('5')
        model.pressOp(com.corriente.money.CalcOp.PLUS)
        model.pressDigit('3'); model.pressDecimalPoint(); model.pressDigit('2')
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasPendingCalc)
        assertTrue(model.uiState.value.canSave) // резолвится 15.70

        model.pressEquals()
        advanceUntilIdle()
        assertFalse(model.uiState.value.hasPendingCalc)
        assertTrue(model.save())
        advanceUntilIdle()

        assertEquals(1570L, fakes.txnDao.rows.value.single().amountMinor)
    }

    @Test
    fun `chained subtraction driving the accumulator negative does not crash the amount field`() =
        runTest(dispatcher) {
            val fakes = Fakes().apply { seed() }
            val model = vm(fakes)
            backgroundScope.observe(model)
            advanceUntilIdle()

            // 5 − 10 − 3 : после второго «−» накопитель = −5 (AmountInput.fromMinor его не принял бы)
            model.pressDigit('5')
            model.pressOp(com.corriente.money.CalcOp.MINUS)
            model.pressDigit('1'); model.pressDigit('0')
            model.pressOp(com.corriente.money.CalcOp.MINUS)
            model.pressDigit('3')
            advanceUntilIdle()

            assertEquals("−5 − 3", model.uiState.value.amountText) // не бросает, знак вынесен в строку
            assertFalse(model.uiState.value.canSave)                // итог −8, сохранять нечего

            // добить до плюса: −8 + 20 = 12
            model.pressOp(com.corriente.money.CalcOp.PLUS)
            model.pressDigit('2'); model.pressDigit('0')
            model.pressEquals()
            advanceUntilIdle()
            assertFalse(model.uiState.value.hasPendingCalc)
            assertTrue(model.uiState.value.canSave)
            assertTrue(model.save())
            advanceUntilIdle()
            assertEquals(1200L, fakes.txnDao.rows.value.single().amountMinor)
        }

    @Test
    fun `saving an expense writes a positive amount in the account currency with the chosen category`() =
        runTest(dispatcher) {
            val fakes = Fakes().apply { seed() }
            val model = vm(fakes)
            backgroundScope.observe(model)
            advanceUntilIdle()

            model.selectCategory("cat-food")
            model.pressDigit('1'); model.pressDigit('2'); model.pressDecimalPoint(); model.pressDigit('5')
            model.setNote("  обед  ")
            advanceUntilIdle()
            assertTrue(model.save())
            advanceUntilIdle()

            val row = fakes.txnDao.rows.value.single()
            assertEquals(TxnKind.EXPENSE, row.kind)
            assertEquals(1250L, row.amountMinor)          // 12.50, положительное (I-1)
            assertEquals("RUB", row.currencyCode)          // из счёта (I-15)
            assertEquals("acc-rub", row.accountId)
            assertEquals("cat-food", row.categoryId)
            assertEquals("обед", row.note)
            assertEquals(today.toString(), row.date)
            assertTrue(model.finished.value)
        }

    @Test
    fun `saving an income uses the INCOME branch and allows no category`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.setKind(EntryKind.INCOME)
        advanceUntilIdle()
        assertEquals(listOf("Зарплата"), model.uiState.value.categories.map { it.name })

        model.pressDigit('9'); model.pressDigit('0'); model.pressDigit('0')
        advanceUntilIdle()
        assertTrue(model.save())
        advanceUntilIdle()

        val row = fakes.txnDao.rows.value.single()
        assertEquals(TxnKind.INCOME, row.kind)
        assertEquals(90000L, row.amountMinor)
        assertNull(row.categoryId)
    }

    @Test
    fun `switching kind clears a category that no longer matches`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        model.selectCategory("cat-food")
        advanceUntilIdle()
        assertEquals("cat-food", model.uiState.value.selectedCategoryId)

        model.setKind(EntryKind.INCOME)
        advanceUntilIdle()
        assertNull(model.uiState.value.selectedCategoryId)
    }

    @Test
    fun `edit mode prefills from the existing expense and save updates it in place`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        // сначала создаём операцию через отдельную VM
        val creator = vm(fakes)
        backgroundScope.observe(creator)
        advanceUntilIdle()
        creator.selectCategory("cat-food")
        creator.pressDigit('5'); creator.pressDigit('0'); creator.pressDigit('0')
        creator.setNote("такси")
        advanceUntilIdle()
        creator.save()
        advanceUntilIdle()
        val id = fakes.txnDao.rows.value.single().id

        val editor = vm(fakes, editingTxnId = id)
        backgroundScope.observe(editor)
        advanceUntilIdle()
        assertTrue(editor.isEditing)
        assertEquals("500", editor.uiState.value.amountText)
        assertEquals("cat-food", editor.uiState.value.selectedCategoryId)
        assertEquals("такси", editor.uiState.value.note)

        editor.pressDigit('0')          // 5000
        editor.selectCategory(null)
        advanceUntilIdle()
        assertTrue(editor.save())
        advanceUntilIdle()

        val row = fakes.txnDao.rows.value.single()   // всё та же одна операция
        assertEquals(id, row.id)
        assertEquals(500000L, row.amountMinor)
        assertNull(row.categoryId)
    }

    // F0.3 — правка операции на архивном счёте раньше уводила её на первый активный счёт.
    @Test
    fun `editing a txn on an archived account keeps it on that account`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val creator = vm(fakes)
        backgroundScope.observe(creator)
        advanceUntilIdle()
        creator.pressDigit('5'); creator.pressDigit('0'); creator.pressDigit('0')
        advanceUntilIdle()
        creator.save()
        advanceUntilIdle()
        val id = fakes.txnDao.rows.value.single().id

        val rub = fakes.accountDao.getById("acc-rub")!!
        fakes.accountDao.update(rub.copy(isArchived = true)) // счёт RUB ушёл в архив, активен только CLP

        val editor = vm(fakes, editingTxnId = id)
        backgroundScope.observe(editor)
        advanceUntilIdle()
        assertEquals("acc-rub", editor.uiState.value.selectedAccountId)
        assertTrue(editor.uiState.value.accounts.single { it.id == "acc-rub" }.isArchived)

        editor.setNote("правка")
        advanceUntilIdle()
        assertTrue(editor.save())
        advanceUntilIdle()
        val row = fakes.txnDao.rows.value.single()
        assertEquals("acc-rub", row.accountId)
        assertEquals("RUB", row.currencyCode)
    }

    // F0.4 — правка операции с архивной категорией раньше обнуляла категорию.
    @Test
    fun `editing a txn with an archived category keeps the category`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val creator = vm(fakes)
        backgroundScope.observe(creator)
        advanceUntilIdle()
        creator.selectCategory("cat-food")
        creator.pressDigit('1'); creator.pressDigit('0'); creator.pressDigit('0')
        advanceUntilIdle()
        creator.save()
        advanceUntilIdle()
        val id = fakes.txnDao.rows.value.single().id

        val food = fakes.categoryDao.getById("cat-food")!!
        fakes.categoryDao.update(food.copy(isArchived = true))

        val editor = vm(fakes, editingTxnId = id)
        backgroundScope.observe(editor)
        advanceUntilIdle()
        assertEquals("cat-food", editor.uiState.value.selectedCategoryId)
        assertTrue(editor.uiState.value.categories.any { it.id == "cat-food" && it.isArchived })

        editor.pressDigit('0') // сумма меняется
        advanceUntilIdle()
        assertTrue(editor.save())
        advanceUntilIdle()
        assertEquals("cat-food", fakes.txnDao.rows.value.single().categoryId)
    }

    // F0.2 — раньше исключение репозитория в viewModelScope убивало процесс.
    @Test
    fun `a repository failure on save surfaces a message and keeps the form`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        fakes.txnDao.failWith = IllegalStateException("boom")
        val model = vm(fakes)
        backgroundScope.observe(model)
        backgroundScope.launch { model.messages.collect {} }
        advanceUntilIdle()

        model.selectCategory("cat-food")
        model.pressDigit('5'); model.pressDigit('0')
        advanceUntilIdle()
        assertTrue(model.save())
        advanceUntilIdle()

        assertNotNull(model.messages.value)
        assertFalse(model.finished.value)
        assertTrue(fakes.txnDao.rows.value.isEmpty())
        assertTrue(model.uiState.value.canSave) // сумма и категория на месте
    }

    @Test
    fun `switching to a zero-minor-unit account re-caps the typed amount`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.pressDigit('7'); model.pressDecimalPoint(); model.pressDigit('5')  // 7.5 RUB
        advanceUntilIdle()
        model.selectAccount("acc-clp")
        advanceUntilIdle()

        assertEquals("CLP", model.uiState.value.currency?.code?.code)
        assertEquals("7", model.uiState.value.amountText) // дробная часть отброшена под CLP
        assertTrue(model.save())
        advanceUntilIdle()
        assertEquals(7L, fakes.txnDao.rows.value.single().amountMinor)
        assertEquals("CLP", fakes.txnDao.rows.value.single().currencyCode)
    }
}
