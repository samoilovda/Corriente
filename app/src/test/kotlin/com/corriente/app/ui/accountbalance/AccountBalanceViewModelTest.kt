package com.corriente.app.ui.accountbalance

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.app.ui.txnentry.FakeTxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** R3.2: экран «динамика по счёту» — сквозной сценарий поверх [com.corriente.data.usecase.balanceSeries]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountBalanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 5, 20)

    private class Fakes {
        val accountDao = FakeAccountDao()
        val txnDao = FakeTxnDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
    }

    private fun vm(fakes: Fakes, initialAccountId: String? = null) = AccountBalanceViewModel(
        accounts = AccountRepository(fakes.accountDao),
        txns = TxnRepository(fakes.txnDao, fakes.accountDao),
        currencies = CurrencyRepository(fakes.currencyDao),
        initialAccountId = initialAccountId,
        today = { today },
    )

    private fun CoroutineScope.observe(model: AccountBalanceViewModel) = launch { model.uiState.collect {} }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `defaults to the first active account and shows a daily series for the month`() = runTest(dispatcher) {
        val fakes = Fakes().apply {
            accountDao.insert(AccountEntity("acc", "Наличные", "RUB", AccountKind.CASH, 1_000_00, 0))
        }
        TxnRepository(fakes.txnDao, fakes.accountDao)
            .addExpense("acc", Money(Minor(100_00), com.corriente.money.CurrencyCode("RUB")), null, LocalDate.of(2026, 5, 3), null)

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals("acc", state.selectedAccountId)
        assertEquals(31, state.points.size) // май — 31 день
        assertEquals(1_000_00L, state.points.first().valueMinor) // остаток на 1 мая — ещё без расхода
        assertEquals(900_00L, state.points.last().valueMinor) // остаток на 31 мая — после расхода
        assertTrue(state.currentBalanceText!!.isNotBlank())
    }

    @Test
    fun `selecting another account switches the series to that account's own history`() = runTest(dispatcher) {
        val fakes = Fakes().apply {
            accountDao.insert(AccountEntity("a", "A", "RUB", AccountKind.CASH, 100_00, 0))
            accountDao.insert(AccountEntity("b", "B", "RUB", AccountKind.CASH, 200_00, 1))
        }
        val model = vm(fakes, initialAccountId = "a")
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertEquals(100_00L, model.uiState.value.points.first().valueMinor)

        model.selectAccount("b")
        advanceUntilIdle()
        assertEquals("b", model.uiState.value.selectedAccountId)
        assertEquals(200_00L, model.uiState.value.points.first().valueMinor)
    }
}
