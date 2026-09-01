package com.corriente.app.ui.report

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.app.ui.txnentry.FakeTxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.ReportKind
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

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val rub = CurrencyCode("RUB")
    private val today = LocalDate.of(2026, 5, 20)

    private class Fakes {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(
            listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0), CurrencyEntity("USD", 2, 2, "$", true, 1)),
        )
        val categoryDao = FakeCategoryDao()
    }

    private suspend fun Fakes.seed() {
        accountDao.insert(AccountEntity("acc", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        accountDao.insert(AccountEntity("accu", "USD", "USD", AccountKind.CASH, 0, 0))
        categoryDao.insert(CategoryEntity("food", "Еда", CategoryKind.EXPENSE, color = 0))
        categoryDao.insert(CategoryEntity("fun", "Развлечения", CategoryKind.EXPENSE, color = 0))
    }

    private fun vm(fakes: Fakes) = ReportViewModel(
        txns = TxnRepository(fakes.txnDao, fakes.accountDao),
        categories = CategoryRepository(fakes.categoryDao),
        currencies = CurrencyRepository(fakes.currencyDao),
        today = { today },
    )

    private fun repo(fakes: Fakes) = TxnRepository(fakes.txnDao, fakes.accountDao)

    private fun CoroutineScope.observe(model: ReportViewModel) = launch { model.uiState.collect {} }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // --- чистые функции ---

    @Test
    fun `withShares computes integer percentages against the grand total`() {
        val report = listOf(
            com.corriente.data.usecase.CategoryTotal("a", Money(Minor(7_500), rub)),
            com.corriente.data.usecase.CategoryTotal("b", Money(Minor(2_500), rub)),
        )
        val rows = withShares(
            report, mapOf("a" to "A", "b" to "B"), mapOf("a" to 111, "b" to 222),
            com.corriente.money.Currency(rub, 2, 2, "₽"),
        )
        assertEquals(listOf(75, 25), rows.map { it.sharePercent })
        assertEquals(listOf("A", "B"), rows.map { it.name })
        assertEquals(listOf(111, 222), rows.map { it.color })
    }

    @Test
    fun `dominantCurrency picks the currency with the most category transactions`() {
        val txns = listOf(
            com.corriente.data.model.Txn.Expense("1", today, 0, 0, "a", Money(Minor(1), rub), "c"),
            com.corriente.data.model.Txn.Expense("2", today, 0, 0, "a", Money(Minor(1), rub), "c"),
            com.corriente.data.model.Txn.Expense("3", today, 0, 0, "a", Money(Minor(1), CurrencyCode("USD")), "c"),
        )
        assertEquals("RUB", dominantCurrency(txns))
        assertNull(dominantCurrency(emptyList()))
    }

    // --- ViewModel ---

    @Test
    fun `report lists categories with shares for the current month in the dominant currency`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val r = repo(fakes)
        r.addExpense("acc", Money(Minor(30_000), rub), "food", LocalDate.of(2026, 5, 3), null)
        r.addExpense("acc", Money(Minor(10_000), rub), "food", LocalDate.of(2026, 5, 9), null)
        r.addExpense("acc", Money(Minor(10_000), rub), "fun", LocalDate.of(2026, 5, 10), null)
        r.addExpense("acc", Money(Minor(99_999), rub), "food", LocalDate.of(2026, 4, 30), null) // прошлый месяц

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()

        val rows = model.uiState.value.rows
        assertEquals(listOf("Еда", "Развлечения"), rows.map { it.name })
        assertEquals(listOf(80, 20), rows.map { it.sharePercent })
        assertEquals("RUB", model.uiState.value.selectedCurrency)
        assertEquals("05.2026", model.uiState.value.periodLabel)
    }

    @Test
    fun `shifting the period back excludes this month's transactions`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        repo(fakes).addExpense("acc", Money(Minor(1_000), rub), "food", LocalDate.of(2026, 5, 3), null)

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.rows.size)

        model.shiftPeriod(-1)
        advanceUntilIdle()
        assertEquals("04.2026", model.uiState.value.periodLabel)
        assertTrue(model.uiState.value.rows.isEmpty())
    }

    @Test
    fun `drilldown lists the transactions behind a category`() = runTest(dispatcher) {
        val fakes = Fakes().apply { seed() }
        val r = repo(fakes)
        r.addExpense("acc", Money(Minor(30_000), rub), "food", LocalDate.of(2026, 5, 3), "магазин")
        r.addExpense("acc", Money(Minor(10_000), rub), "food", LocalDate.of(2026, 5, 9), null)

        val model = vm(fakes)
        backgroundScope.observe(model)
        advanceUntilIdle()
        model.openDrilldown("food")
        advanceUntilIdle()

        val dd = model.uiState.value.drilldown!!
        assertEquals("Еда", dd.categoryName)
        assertEquals(2, dd.txns.size)
        assertEquals("магазин", dd.txns.first { it.note != null }.note)

        model.closeDrilldown()
        advanceUntilIdle()
        assertNull(model.uiState.value.drilldown)
    }
}
