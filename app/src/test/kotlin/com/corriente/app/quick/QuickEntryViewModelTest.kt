package com.corriente.app.quick

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.app.ui.txnentry.EntryKind
import com.corriente.app.ui.txnentry.FakeTxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.repository.AccountRepository
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

/** R4.2 — окно быстрого ввода из виджета: расход/доход и выбор счёта, не трогающий виджет. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 15)

    private class Fakes {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(
            listOf(
                CurrencyEntity("RUB", 2, 2, "₽", true, 0),
                CurrencyEntity("USD", 2, 2, "$", true, 1),
            ),
        )
    }

    private suspend fun Fakes.seed() {
        accountDao.insert(entityAccount("acc-rub", "Наличные", "RUB"))
        accountDao.insert(entityAccount("acc-usd", "Доллары", "USD"))
    }

    private fun entityAccount(id: String, name: String, code: String) = AccountEntity(
        id = id, name = name, currencyCode = code, kind = AccountKind.CASH,
        openingBalanceMinor = 0, color = 0,
    )

    private fun vm(
        fakes: Fakes,
        categoryId: String? = "cat-food",
        categoryName: String = "Еда",
        initialAccountId: String? = "acc-rub",
    ): QuickEntryViewModel = QuickEntryViewModel(
        txnRepository = TxnRepository(fakes.txnDao, fakes.accountDao),
        accountRepository = AccountRepository(fakes.accountDao),
        currencyRepository = CurrencyRepository(fakes.currencyDao),
        categoryId = categoryId,
        categoryName = categoryName,
        initialAccountId = initialAccountId,
        today = { today },
    )

    private fun CoroutineScope.observe(model: QuickEntryViewModel) {
        launch { model.uiState.collect {} }
        launch { model.finished.collect {} }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opens on the widget's active account and the expense branch by default`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        assertEquals(EntryKind.EXPENSE, model.uiState.value.kind)
        assertEquals("acc-rub", model.uiState.value.selectedAccountId)
        assertEquals("RUB", model.uiState.value.currency?.code?.code)
        assertEquals("Еда", model.uiState.value.categoryName)
    }

    @Test
    fun `falls back to the first account when the widget's active account no longer exists`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes, initialAccountId = "gone")
        backgroundScope.observe(model)
        advanceUntilIdle()

        assertEquals("acc-rub", model.uiState.value.selectedAccountId)
    }

    @Test
    fun `toggling to income switches the branch and clears the amount`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.pressDigit('5')
        model.setKind(EntryKind.INCOME)
        advanceUntilIdle()

        assertEquals(EntryKind.INCOME, model.uiState.value.kind)
        assertTrue(model.uiState.value.amount.isEmpty)
    }

    @Test
    fun `saving an income from the widget writes an INCOME row`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.setKind(EntryKind.INCOME)
        model.pressDigit('5'); model.pressDigit('0'); model.pressDigit('0')
        advanceUntilIdle()
        assertTrue(model.save())
        advanceUntilIdle()

        val row = fakes.txnDao.rows.value.single()
        assertEquals(TxnKind.INCOME, row.kind)
        assertEquals(50000L, row.amountMinor)
        assertEquals("acc-rub", row.accountId)
        assertTrue(model.finished.value)
    }

    @Test
    fun `choosing a different account in-window applies only to this transaction`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes, initialAccountId = "acc-rub")
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.selectAccount("acc-usd")
        model.pressDigit('7'); model.pressDigit('5')
        advanceUntilIdle()
        assertEquals("USD", model.uiState.value.currency?.code?.code)
        assertTrue(model.save())
        advanceUntilIdle()

        val row = fakes.txnDao.rows.value.single()
        assertEquals("acc-usd", row.accountId)
        assertEquals("USD", row.currencyCode)
        assertEquals(75L, row.amountMinor)
        // R4.2: выбор счёта в окне не пишется никуда, кроме этой операции — WidgetConfigStore
        // тут вообще не участвует (ViewModel его не знает), так что «активный счёт виджета»
        // остаётся прежним по построению.
    }

    @Test
    fun `cannot save with an empty amount`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        assertFalse(model.uiState.value.canSave)
        assertFalse(model.save())
        assertTrue(fakes.txnDao.rows.value.isEmpty())
    }

    @Test
    fun `a repository failure surfaces a message and does not finish`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        fakes.txnDao.failWith = IllegalStateException("boom")
        val model = vm(fakes)
        backgroundScope.observe(model)
        backgroundScope.launch { model.messages.collect {} }
        advanceUntilIdle()

        model.pressDigit('5'); model.pressDigit('0')
        advanceUntilIdle()
        assertTrue(model.save())
        advanceUntilIdle()

        assertNotNull(model.messages.value)
        assertFalse(model.finished.value)
        assertTrue(fakes.txnDao.rows.value.isEmpty())
        assertFalse(model.uiState.value.saving) // не застряло в "saving" после ошибки
    }

    @Test
    fun `passes the category id through regardless of the selected kind`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes, categoryId = "cat-salary")
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.setKind(EntryKind.INCOME)
        model.pressDigit('1')
        advanceUntilIdle()
        assertTrue(model.save())
        advanceUntilIdle()

        assertEquals("cat-salary", fakes.txnDao.rows.value.single().categoryId)
    }

    @Test
    fun `a null category (e.g. no matching widget tile) saves without one`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes, categoryId = null)
        backgroundScope.observe(model)
        advanceUntilIdle()

        model.pressDigit('1')
        advanceUntilIdle()
        assertTrue(model.save())
        advanceUntilIdle()

        assertNull(fakes.txnDao.rows.value.single().categoryId)
    }
}
