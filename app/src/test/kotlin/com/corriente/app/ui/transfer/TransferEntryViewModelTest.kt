package com.corriente.app.ui.transfer

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransferEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 7, 1)

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
        accountDao.insert(AccountEntity("rub1", "Наличные ₽", "RUB", AccountKind.CASH, 0, 0))
        accountDao.insert(AccountEntity("rub2", "Карта ₽", "RUB", AccountKind.CARD, 0, 0))
        accountDao.insert(AccountEntity("usd1", "Доллары", "USD", AccountKind.CASH, 0, 0))
    }

    private fun vm(fakes: Fakes, editingTxnId: String? = null) = TransferEntryViewModel(
        txns = TxnRepository(fakes.txnDao, fakes.accountDao),
        accounts = AccountRepository(fakes.accountDao),
        currencies = CurrencyRepository(fakes.currencyDao),
        editingTxnId = editingTxnId,
        today = { today },
    )

    private fun CoroutineScope.observe(model: TransferEntryViewModel) {
        launch { model.uiState.collect {} }
        launch { model.finished.collect {} }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `same-currency transfer hides the second amount and mirrors the first`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        // по умолчанию from=rub1, to=rub2 (обе RUB)
        model.setFromAmount("1500")
        advanceUntilIdle()
        val s = model.uiState.value
        assertTrue(s.sameCurrency)
        assertNull(s.derivedRateLabel)
        assertTrue(s.canSave)

        model.save()
        advanceUntilIdle()
        val row = fakes.txnDao.rows.value.single()
        assertEquals(TxnKind.TRANSFER, row.kind)
        assertEquals(150000L, row.amountMinor)
        assertEquals(150000L, row.toAmountMinor)  // зачислено = отправлено
    }

    @Test
    fun `cross-currency — manual rate recomputes the received amount and the derived rate is shown`() =
        runTest(dispatcher) {
            val fakes = Fakes().apply { seed() }
            val model = vm(fakes)
            backgroundScope.observe(model)
            advanceUntilIdle()
            model.selectTo("usd1")              // from=rub1 (RUB), to=usd1 (USD)
            advanceUntilIdle()
            assertFalse(model.uiState.value.sameCurrency)

            model.setFromAmount("8695")
            model.setRate("0.011501")           // 1 RUB = 0.011501 USD
            advanceUntilIdle()
            assertEquals("100", model.uiState.value.toAmountText)   // 8695 * 0.011501 ≈ 100.00
            assertEquals("1 USD = 86.95 RUB", model.uiState.value.derivedRateLabel)

            model.save()
            advanceUntilIdle()
            val row = fakes.txnDao.rows.value.single()
            assertEquals(869500L, row.amountMinor)
            assertEquals("RUB", row.currencyCode)
            assertEquals(10000L, row.toAmountMinor)
            assertEquals("USD", row.toCurrencyCode)
        }

    @Test
    fun `cross-currency — entering both amounts yields the deal rate without a manual rate`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        model.selectTo("usd1")
        advanceUntilIdle()
        model.setFromAmount("8695")
        model.setToAmount("100")
        advanceUntilIdle()
        assertEquals("1 USD = 86.95 RUB", model.uiState.value.derivedRateLabel)
    }

    @Test
    fun `a negative or zero manual rate is ignored instead of crashing the received amount`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        model.selectTo("usd1")
        advanceUntilIdle()
        model.setFromAmount("8695")
        model.setToAmount("100")
        advanceUntilIdle()

        model.setRate("-0.5")   // KeyboardType.Decimal на части IME пропускает «−»
        advanceUntilIdle()
        assertEquals("100", model.uiState.value.toAmountText)  // не тронут, не упал

        model.setRate("0")
        advanceUntilIdle()
        assertEquals("100", model.uiState.value.toAmountText)

        model.setRate("0.011501")   // валидный курс всё ещё пересчитывает
        advanceUntilIdle()
        assertEquals("100", model.uiState.value.toAmountText)
    }

    @Test
    fun `cannot transfer an account into itself`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        model.selectTo("rub1")   // == from
        model.setFromAmount("100")
        advanceUntilIdle()
        assertFalse(model.uiState.value.canSave)
        assertFalse(model.save())
    }

    @Test
    fun `edit mode loads an existing transfer`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val repo = TxnRepository(fakes.txnDao, fakes.accountDao)
        val transfer = repo.addTransfer(
            "rub1", com.corriente.money.Money(com.corriente.money.Minor(500_00), com.corriente.money.CurrencyCode("RUB")),
            "usd1", com.corriente.money.Money(com.corriente.money.Minor(5_00), com.corriente.money.CurrencyCode("USD")),
            today, "старый",
        )
        val model = vm(fakes, editingTxnId = transfer.id)
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertTrue(model.isEditing)
        assertEquals("500", model.uiState.value.fromAmountText)
        assertEquals("5", model.uiState.value.toAmountText)
        assertEquals("старый", model.uiState.value.note)
    }
}
