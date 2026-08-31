package com.corriente.app.ui.currencies

import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.model.toManaged
import com.corriente.data.repository.CurrencyRepository
import com.corriente.money.CurrencyCode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrenciesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun currency(code: String, minorUnits: Int, active: Boolean) = CurrencyEntity(
        code = code,
        minorUnits = minorUnits,
        displayScale = minorUnits,
        symbol = code,
        isActive = active,
        displayOrder = 0,
    )

    private val seed = listOf(
        currency("USD", 2, active = true),
        currency("RUB", 2, active = true),
        currency("KZT", 2, active = false),
        currency("CLP", 0, active = false),
    )

    private val managed = seed.map { it.toManaged() }

    private fun viewModel(dao: FakeCurrencyDao = FakeCurrencyDao(seed)): Pair<CurrenciesViewModel, FakeCurrencyDao> =
        CurrenciesViewModel(CurrencyRepository(dao)) to dao

    private fun CoroutineScope.startCollecting(vm: CurrenciesViewModel) {
        launch { vm.uiState.collect {} }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- чистые функции ---

    @Test
    fun `filter by code is case-insensitive and ignores surrounding whitespace`() {
        assertEquals(listOf("USD"), filterCurrencies(managed, "usd").map { it.code.code })
        assertEquals(listOf("USD"), filterCurrencies(managed, "  UsD  ").map { it.code.code })
    }

    @Test
    fun `filter matches a substring of either code or name`() {
        // "us" встречается и в коде USD, и в названии "Russian Ruble" — обе строки валидны.
        assertEquals(setOf("USD", "RUB"), filterCurrencies(managed, "us").map { it.code.code }.toSet())
    }

    @Test
    fun `filter by english name`() {
        assertEquals(listOf("CLP"), filterCurrencies(managed, "chilean").map { it.code.code })
    }

    @Test
    fun `empty query returns the whole list`() {
        assertEquals(managed, filterCurrencies(managed, "   "))
    }

    @Test
    fun `display scale must stay within minor units`() {
        assertTrue(isValidDisplayScale(minorUnits = 2, displayScale = 0))
        assertTrue(isValidDisplayScale(minorUnits = 2, displayScale = 2))
        assertFalse(isValidDisplayScale(minorUnits = 2, displayScale = 3))
        assertFalse(isValidDisplayScale(minorUnits = 0, displayScale = 1))
        assertFalse(isValidDisplayScale(minorUnits = 2, displayScale = -1))
    }

    // --- ViewModel ---

    @Test
    fun `uiState exposes the full seeded catalogue`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        backgroundScope.startCollecting(vm)
        advanceUntilIdle()
        assertEquals(setOf("USD", "RUB", "KZT", "CLP"), vm.uiState.value.currencies.map { it.code.code }.toSet())
    }

    @Test
    fun `changing the query filters the visible list`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        backgroundScope.startCollecting(vm)
        vm.onQueryChange("kzt")
        advanceUntilIdle()
        assertEquals(listOf("KZT"), vm.uiState.value.currencies.map { it.code.code })
    }

    @Test
    fun `setActive enables a currency and it is reflected in state`() = runTest(dispatcher) {
        val (vm, dao) = viewModel()
        backgroundScope.startCollecting(vm)
        vm.setActive(CurrencyCode("KZT"), true)
        advanceUntilIdle()
        assertEquals(true, dao.getByCode("KZT")?.isActive)
        assertEquals(true, vm.uiState.value.currencies.first { it.code.code == "KZT" }.isActive)
    }

    @Test
    fun `updateDisplay writes a valid symbol and scale`() = runTest(dispatcher) {
        val (vm, dao) = viewModel()
        backgroundScope.startCollecting(vm)
        vm.updateDisplay(CurrencyCode("USD"), symbol = "US$", displayScale = 0, minorUnits = 2)
        advanceUntilIdle()
        val row = dao.getByCode("USD")!!
        assertEquals("US$", row.symbol)
        assertEquals(0, row.displayScale)
    }

    @Test
    fun `updateDisplay ignores a scale above minor units - never reaches the repository`() = runTest(dispatcher) {
        val (vm, dao) = viewModel()
        backgroundScope.startCollecting(vm)
        vm.updateDisplay(CurrencyCode("CLP"), symbol = "$", displayScale = 2, minorUnits = 0)
        advanceUntilIdle()
        assertEquals(0, dao.getByCode("CLP")?.displayScale)
    }
}
