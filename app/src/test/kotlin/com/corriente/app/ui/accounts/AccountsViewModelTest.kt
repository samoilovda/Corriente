package com.corriente.app.ui.accounts

import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val clp = Currency(CurrencyCode("CLP"), minorUnits = 0, displayScale = 0, symbol = "$")

    private fun currencyEntity(code: String, minorUnits: Int, active: Boolean) = CurrencyEntity(
        code = code,
        minorUnits = minorUnits,
        displayScale = minorUnits,
        symbol = code,
        isActive = active,
        displayOrder = 0,
    )

    private fun build(): Triple<AccountsViewModel, FakeAccountDao, CurrencyRepository> {
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(
            listOf(
                currencyEntity("RUB", 2, active = true),
                currencyEntity("USD", 2, active = true),
                currencyEntity("CLP", 0, active = false),
            ),
        )
        val currencies = CurrencyRepository(currencyDao)
        val vm = AccountsViewModel(AccountRepository(accountDao), currencies)
        return Triple(vm, accountDao, currencies)
    }

    private fun CoroutineScope.observe(vm: AccountsViewModel) {
        launch { vm.uiState.collect {} }
        launch { vm.editor.collect {} }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- чистые функции ---

    @Test
    fun `opening balance parses magnitude and sign, locale-independent`() {
        assertEquals(Minor(123456), openingBalanceMinor("1234.56", rub))
        assertEquals(Minor(123456), openingBalanceMinor("1234,56", rub))
        assertEquals(Minor(-50000), openingBalanceMinor("-500", rub))
        assertEquals(Minor(0), openingBalanceMinor("", rub))
        assertEquals(Minor(500), openingBalanceMinor("500.99", clp)) // CLP: дробная часть отбрасывается
    }

    @Test
    fun `opening balance text round-trips through the parser`() {
        for (raw in listOf(0L, 5L, 100L, 123456L, -7500L)) {
            val text = openingBalanceText(Minor(raw), rub)
            assertEquals(Minor(raw), openingBalanceMinor(text, rub))
        }
        assertEquals("", openingBalanceText(Minor(0), rub))
        assertEquals("1234.56", openingBalanceText(Minor(123456), rub))
        assertEquals("-75.00", openingBalanceText(Minor(-7500), rub))
    }

    // --- ViewModel ---

    @Test
    fun `startCreate opens an editor defaulted to the first active currency`() = runTest(dispatcher) {
        val (vm, _, _) = build()
        backgroundScope.observe(vm)
        advanceUntilIdle()
        vm.startCreate()
        advanceUntilIdle()
        val editor = vm.editor.value!!
        assertNull(editor.editingId)
        assertFalse(editor.currencyLocked)
        assertEquals("RUB", editor.currency?.code)
    }

    @Test
    fun `save creates an account that shows up in the active list`() = runTest(dispatcher) {
        val (vm, _, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate()
        advanceUntilIdle()
        val ok = vm.save(
            AccountForm("Наличные", CurrencyCode("RUB"), AccountKind.CASH, "10000", includeInTotal = true),
        )
        advanceUntilIdle()
        assertTrue(ok)
        val rows = vm.uiState.value.active
        assertEquals(1, rows.size)
        assertEquals("Наличные", rows.single().account.name)
        assertEquals(Minor(1_000_000), rows.single().account.openingBalance.amount)
        assertNull(vm.editor.value)
    }

    @Test
    fun `save rejects a blank name and keeps the editor open`() = runTest(dispatcher) {
        val (vm, _, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate()
        advanceUntilIdle()
        assertFalse(vm.save(AccountForm("  ", CurrencyCode("RUB"), AccountKind.CASH, "1", includeInTotal = true)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.active.isEmpty())
    }

    @Test
    fun `editing an account with transactions locks the currency and save cannot change it`() =
        runTest(dispatcher) {
            val (vm, dao, _) = build()
            backgroundScope.observe(vm)
            vm.startCreate()
            advanceUntilIdle()
            vm.save(AccountForm("Карта", CurrencyCode("RUB"), AccountKind.CARD, "0", includeInTotal = true))
            advanceUntilIdle()

            val account = vm.uiState.value.active.single().account
            dao.markHasTransactions(account.id)

            vm.startEdit(account)
            advanceUntilIdle()
            assertTrue(vm.editor.value!!.currencyLocked)

            // Пытаемся сменить валюту на USD — должно быть проигнорировано (I-23).
            vm.save(AccountForm("Карта", CurrencyCode("USD"), AccountKind.CARD, "0", includeInTotal = true))
            advanceUntilIdle()
            assertEquals("RUB", vm.uiState.value.active.single().account.currency.code)
        }

    @Test
    fun `archive then unarchive moves the account between lists`() = runTest(dispatcher) {
        val (vm, _, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate()
        advanceUntilIdle()
        vm.save(AccountForm("Копилка", CurrencyCode("RUB"), AccountKind.SAVINGS, "0", includeInTotal = true))
        advanceUntilIdle()
        val id = vm.uiState.value.active.single().account.id

        vm.archive(id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.active.isEmpty())
        assertEquals(listOf(id), vm.uiState.value.archived.map { it.account.id })

        vm.unarchive(id)
        advanceUntilIdle()
        assertEquals(listOf(id), vm.uiState.value.active.map { it.account.id })
    }
}
