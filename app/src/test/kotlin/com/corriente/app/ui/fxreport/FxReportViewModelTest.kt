package com.corriente.app.ui.fxreport

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.app.ui.txnentry.FakeTxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** R3.4: «сколько стоили конвертации в этом году» — сквозной сценарий поверх conversionCost. */
@OptIn(ExperimentalCoroutinesApi::class)
class FxReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 5, 20)
    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    private class Fakes {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(
            listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0), CurrencyEntity("USD", 2, 2, "$", true, 1)),
        )
    }

    private suspend fun Fakes.seedAccounts() {
        accountDao.insert(AccountEntity("usd", "USD", "USD", AccountKind.CASH, 0, 0))
        accountDao.insert(AccountEntity("rub", "RUB", "RUB", AccountKind.CASH, 0, 1))
    }

    private fun vm(fakes: Fakes) = FxReportViewModel(
        txns = TxnRepository(fakes.txnDao, fakes.accountDao),
        currencies = CurrencyRepository(fakes.currencyDao),
        today = { today },
    )

    private fun CoroutineScope.observe(model: FxReportViewModel) = launch { model.uiState.collect {} }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `fewer than three deals in the pair shows insufficient data, not a zero`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seedAccounts() }
        val r = TxnRepository(fakes.txnDao, fakes.accountDao)
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(9_000_00), rub), LocalDate.of(2026, 1, 5))
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(9_100_00), rub), LocalDate.of(2026, 2, 5))

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        val cost = model.uiState.value.conversionCosts.single()
        assertEquals(2, cost.dealCount)
        assertTrue(cost.insufficientData)
        assertNull(cost.amountText)
    }

    @Test
    fun `three or more deals in the same year produce an estimated amount`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seedAccounts() }
        val r = TxnRepository(fakes.txnDao, fakes.accountDao)
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(8_500_00), rub), LocalDate.of(2026, 1, 5))
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(9_000_00), rub), LocalDate.of(2026, 2, 5))
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(9_500_00), rub), LocalDate.of(2026, 3, 5))

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        val cost = model.uiState.value.conversionCosts.single()
        assertEquals(3, cost.dealCount)
        assertTrue(!cost.insufficientData)
        assertTrue(cost.amountText!!.isNotBlank())
    }

    // R3.4 — критерий приёмки: только «в этом году», сделки прошлых лет в подсчёт не идут.
    @Test
    fun `deals from a previous year do not count towards this year's estimate`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seedAccounts() }
        val r = TxnRepository(fakes.txnDao, fakes.accountDao)
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(8_500_00), rub), LocalDate.of(2025, 1, 5))
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(9_000_00), rub), LocalDate.of(2025, 2, 5))
        r.addTransfer("usd", Money(Minor(100_00), usd), "rub", Money(Minor(9_500_00), rub), LocalDate.of(2025, 3, 5))

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        assertTrue(model.uiState.value.conversionCosts.isEmpty())
    }
}
