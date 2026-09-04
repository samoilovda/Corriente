package com.corriente.app.ui.txnentry

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnEntity
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        savedStateHandle: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
    ): TxnEntryViewModel = TxnEntryViewModel(
        txns = TxnRepository(fakes.txnDao, fakes.accountDao),
        accounts = AccountRepository(fakes.accountDao),
        categories = CategoryRepository(fakes.categoryDao),
        currencies = CurrencyRepository(fakes.currencyDao),
        editingTxnId = editingTxnId,
        initialKind = initialKind,
        today = { today },
        savedStateHandle = savedStateHandle,
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
    fun `calculator multiplies an amount by a plain number`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        // 12.50 × 3 = 37.50 (второй операнд — множитель, не сумма)
        model.pressDigit('1'); model.pressDigit('2'); model.pressDecimalPoint(); model.pressDigit('5')
        model.pressOp(com.corriente.money.CalcOp.TIMES)
        model.pressDigit('3')
        model.pressEquals()
        advanceUntilIdle()

        assertTrue(model.save())
        advanceUntilIdle()
        assertEquals(3750L, fakes.txnDao.rows.value.single().amountMinor)
    }

    @Test
    fun `calculator divides an amount by a plain number`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        // 100.00 ÷ 4 = 25.00
        model.pressDigit('1'); model.pressDigit('0'); model.pressDigit('0')
        model.pressOp(com.corriente.money.CalcOp.DIVIDE)
        model.pressDigit('4')
        model.pressEquals()
        advanceUntilIdle()

        assertTrue(model.save())
        advanceUntilIdle()
        assertEquals(2500L, fakes.txnDao.rows.value.single().amountMinor)
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

    // F2.8 — «5 − 10 =» показывает «−5», сохранение недоступно; «+ 20 =» даёт «15».
    @Test
    fun `a non-positive calculator result is shown and blocks save, then continues`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.pressDigit('5')
        model.pressOp(com.corriente.money.CalcOp.MINUS)
        model.pressDigit('1'); model.pressDigit('0')
        model.pressEquals()
        advanceUntilIdle()
        assertEquals("−5", model.uiState.value.amountText)
        assertFalse(model.uiState.value.canSave)
        assertTrue(model.uiState.value.nonPositiveResult)

        model.pressOp(com.corriente.money.CalcOp.PLUS)
        model.pressDigit('2'); model.pressDigit('0')
        model.pressEquals()
        advanceUntilIdle()
        assertEquals("15", model.uiState.value.amountText)
        assertFalse(model.uiState.value.nonPositiveResult)
        assertTrue(model.uiState.value.canSave)
        assertTrue(model.save())
        advanceUntilIdle()
        assertEquals(1500L, fakes.txnDao.rows.value.single().amountMinor)
    }

    // F2.5 — экран не должен мигать «нет счетов» до загрузки.
    @Test
    fun `uiState starts not loaded and flips to loaded after repositories emit`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        assertFalse(model.uiState.value.loaded)
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertTrue(model.uiState.value.loaded)
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

    // R2.2 — «частые»: строка над клавиатурой на экране нового ввода, тап заполняет форму целиком.
    @Test
    fun `frequent templates surface after repeated entries and fill the form on tap`() = runTest(dispatcher) {
        val fakes = Fakes().apply {
            seed()
            repeat(3) { i ->
                txnDao.insert(
                    TxnEntity(
                        "coffee-$i", TxnKind.EXPENSE, today.minusDays(i.toLong()).toString(), 0, 0,
                        "acc-rub", 300_00, "RUB", categoryId = "cat-food",
                    ),
                )
            }
        }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        assertEquals(1, model.uiState.value.frequentOptions.size)
        val option = model.uiState.value.frequentOptions.single()
        assertEquals(300_00, option.entry.amountMinor)

        // Форма ещё пустая
        assertTrue(model.uiState.value.amount.isEmpty)

        model.applyFrequent(option)
        advanceUntilIdle()

        assertEquals("acc-rub", model.uiState.value.selectedAccountId)
        assertEquals("cat-food", model.uiState.value.selectedCategoryId)
        assertTrue(model.uiState.value.canSave)
    }

    // R2.2: строка «частые» не показывается при редактировании существующей операции.
    @Test
    fun `frequent templates are hidden while editing an existing transaction`() = runTest(dispatcher) {
        val fakes = Fakes().apply {
            seed()
            repeat(3) { i ->
                txnDao.insert(
                    TxnEntity(
                        "coffee-$i", TxnKind.EXPENSE, today.minusDays(i.toLong()).toString(), 0, 0,
                        "acc-rub", 300_00, "RUB", categoryId = "cat-food",
                    ),
                )
            }
        }
        val model = vm(fakes, editingTxnId = "coffee-0")
        backgroundScope.observe(model)
        advanceUntilIdle()

        assertTrue(model.uiState.value.frequentOptions.isEmpty())
    }

    // R5.3: удаление физическое и немедленное (I-22), но снекбар держит копию 5 секунд.
    @Test
    fun `delete then undo restores the transaction with the same id, createdAt and import_hash`() =
        runTest(dispatcher) {
            val fakes = Fakes().apply {
                seed()
                txnDao.insert(
                    TxnEntity(
                        "txn-1", TxnKind.EXPENSE, today.toString(), createdAt = 111L, updatedAt = 111L,
                        accountId = "acc-rub", amountMinor = 500_00, currencyCode = "RUB",
                        categoryId = "cat-food", note = "такси", importHash = "hash-1",
                    ),
                )
            }
            val editor = vm(fakes, editingTxnId = "txn-1")
            backgroundScope.observe(editor)
            advanceUntilIdle()

            // ВАЖНО: не advanceUntilIdle() здесь — он проматал бы виртуальное время сквозь
            // delay(UNDO_WINDOW_MS) внутри deleteEditing и сразу подтвердил бы удаление.
            editor.deleteEditing()
            runCurrent()
            assertTrue("удаление немедленное — строки в БД уже нет", fakes.txnDao.rows.value.isEmpty())
            assertNotNull(editor.pendingUndo.value)
            assertFalse(editor.finished.value)

            editor.undoDelete()
            runCurrent()

            val restored = fakes.txnDao.rows.value.single()
            assertEquals("txn-1", restored.id)
            assertEquals(111L, restored.createdAt)
            assertEquals("hash-1", restored.importHash)
            assertEquals("такси", restored.note)
            assertNull(editor.pendingUndo.value)
            assertTrue(editor.finished.value)
        }

    @Test
    fun `leaving before the undo window expires commits the deletion`() = runTest(dispatcher) {
        val fakes = Fakes().apply {
            seed()
            txnDao.insert(
                TxnEntity(
                    "txn-2", TxnKind.EXPENSE, today.toString(), 0, 0,
                    "acc-rub", 500_00, "RUB", categoryId = "cat-food",
                ),
            )
        }
        val editor = vm(fakes, editingTxnId = "txn-2")
        backgroundScope.observe(editor)
        advanceUntilIdle()

        editor.deleteEditing()
        runCurrent()
        assertNotNull(editor.pendingUndo.value)

        // Таймер снекбара ещё не истёк — отмена всё ещё возможна, строки в БД по-прежнему нет.
        advanceTimeBy(TxnEntryViewModel.UNDO_WINDOW_MS - 1_000L)
        runCurrent()
        assertNotNull(editor.pendingUndo.value)
        assertTrue(fakes.txnDao.rows.value.isEmpty())
        assertFalse(editor.finished.value)

        // Таймер истёк — снекбар закрылся сам, экран может закрываться, отмена больше недоступна.
        advanceTimeBy(2_000L)
        runCurrent()
        assertNull(editor.pendingUndo.value)
        assertTrue(editor.finished.value)
        assertTrue(fakes.txnDao.rows.value.isEmpty())
    }

    // R5.4: устойчивость к пересозданию процесса — форма живёт в SavedStateHandle, а не только
    // в MutableStateFlow, который «Не сохранять действия» уничтожает вместе с процессом.
    @Test
    fun `constructing with a pre-populated SavedStateHandle restores amount, category and note`() =
        runTest(dispatcher) {
            val fakes = Fakes().apply { seed() }
            val savedState = androidx.lifecycle.SavedStateHandle(
                mapOf(
                    "txn_entry.kind" to EntryKind.EXPENSE.name,
                    "txn_entry.amount_text" to "12.50",
                    "txn_entry.account_id" to "acc-rub",
                    "txn_entry.category_id" to "cat-food",
                    "txn_entry.date" to today.toString(),
                    "txn_entry.note" to "восстановлено после смерти процесса",
                ),
            )
            val model = vm(fakes, savedStateHandle = savedState)
            backgroundScope.observe(model)
            advanceUntilIdle()

            assertEquals("12.50", model.uiState.value.amountText)
            assertEquals("acc-rub", model.uiState.value.selectedAccountId)
            assertEquals("cat-food", model.uiState.value.selectedCategoryId)
            assertEquals("восстановлено после смерти процесса", model.uiState.value.note)
            assertTrue(model.uiState.value.canSave)
        }

    @Test
    fun `every form field is mirrored into SavedStateHandle as it changes`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val savedState = androidx.lifecycle.SavedStateHandle()
        val model = vm(fakes, savedStateHandle = savedState)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.selectCategory("cat-food")
        model.pressDigit('7'); model.pressDigit('5')
        model.setNote("такси")
        advanceUntilIdle()

        assertEquals("75", savedState.get<String>("txn_entry.amount_text"))
        assertEquals("cat-food", savedState.get<String>("txn_entry.category_id"))
        assertEquals("такси", savedState.get<String>("txn_entry.note"))
        assertEquals("acc-rub", savedState.get<String>("txn_entry.account_id"))
        assertEquals(EntryKind.EXPENSE.name, savedState.get<String>("txn_entry.kind"))
    }
}
