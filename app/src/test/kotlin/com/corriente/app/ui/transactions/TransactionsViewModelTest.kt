package com.corriente.app.ui.transactions

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.app.ui.txnentry.FakeTxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.Currency
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val d1 = LocalDate.of(2026, 3, 10)
    private val d2 = LocalDate.of(2026, 3, 11)

    private val currenciesByCode = mapOf(
        "RUB" to Currency(rub, 2, 2, "₽"),
        "USD" to Currency(usd, 2, 2, "$"),
    )

    private fun expense(id: String, date: LocalDate, amount: Long, cur: CurrencyCode, cat: String?) =
        Txn.Expense(id, date, 0, 0, "acc", Money(Minor(amount), cur), cat)

    private fun income(id: String, date: LocalDate, amount: Long, cur: CurrencyCode) =
        Txn.Income(id, date, 0, 0, "acc", Money(Minor(amount), cur), null)

    // --- чистая функция ---

    @Test
    fun `sections are newest-first with per-currency day totals, transfers excluded from totals`() {
        val transfer = Txn.Transfer(
            "t", d2, 0, 0, "acc", Money(Minor(5_000_00), rub), "acc2", Money(Minor(50_00), usd),
        )
        val txns = listOf(
            expense("e1", d2, 1_500_00, rub, "food"),
            income("i1", d2, 3_000_00, rub),
            expense("e2", d1, 20_00, usd, null),
            transfer,
        )
        val sections = buildDaySections(
            txns = txns,
            filter = TxnFilter(),
            accountNames = mapOf("acc" to "Наличные", "acc2" to "Карта"),
            categoryNames = mapOf("food" to "Еда"),
            currenciesByCode = currenciesByCode,
        )
        assertEquals(listOf(d2, d1), sections.map { it.date })
        // день d2: доход 3000 − расход 1500 = +1500 ₽; перевод в итог не входит
        assertEquals(listOf("1 500.00 ₽"), sections.first().totals)
        assertEquals(listOf("-20.00 $"), sections.last().totals)
        // строки: расход со знаком минус, доход со знаком плюс, перевод — обе суммы
        val d2Rows = sections.first().rows.associateBy { it.id }
        assertEquals("-1 500.00 ₽", d2Rows.getValue("e1").amountText)
        assertEquals("+3 000.00 ₽", d2Rows.getValue("i1").amountText)
        assertTrue(d2Rows.getValue("t").amountText.contains("→"))
        assertEquals(false, d2Rows.getValue("t").editable)
    }

    // F1.1 — итог дня накапливается через Money (Math.*Exact), не через голый Long.
    @Test
    fun `day total nets expense and income of one currency`() {
        val sections = buildDaySections(
            txns = listOf(income("i", d1, 3_000_00, rub), expense("e", d1, 1_250_00, rub, null)),
            filter = TxnFilter(), accountNames = emptyMap(), categoryNames = emptyMap(),
            currenciesByCode = currenciesByCode,
        )
        assertEquals(listOf("1 750.00 ₽"), sections.single().totals)
    }

    @Test
    fun `day total keeps currencies apart`() {
        val sections = buildDaySections(
            txns = listOf(expense("e1", d1, 100_00, rub, null), expense("e2", d1, 5_00, usd, null)),
            filter = TxnFilter(), accountNames = emptyMap(), categoryNames = emptyMap(),
            currenciesByCode = currenciesByCode,
        )
        assertEquals(listOf("-100.00 ₽", "-5.00 $"), sections.single().totals)
    }

    @Test(expected = ArithmeticException::class)
    fun `day total overflow throws instead of silently wrapping`() {
        buildDaySections(
            txns = listOf(
                income("i1", d1, Long.MAX_VALUE, rub),
                income("i2", d1, 1L, rub),
            ),
            filter = TxnFilter(), accountNames = emptyMap(), categoryNames = emptyMap(),
            currenciesByCode = currenciesByCode,
        )
    }

    @Test
    fun `filter by account and by currency narrows the list`() {
        val txns = listOf(
            Txn.Expense("e1", d1, 0, 0, "cash", Money(Minor(100), rub), null),
            Txn.Expense("e2", d1, 0, 0, "card", Money(Minor(200), usd), null),
        )
        val byCash = buildDaySections(txns, TxnFilter(accountId = "cash"), emptyMap(), emptyMap(), currenciesByCode)
        assertEquals(listOf("e1"), byCash.single().rows.map { it.id })

        val byUsd = buildDaySections(txns, TxnFilter(currencyCode = "USD"), emptyMap(), emptyMap(), currenciesByCode)
        assertEquals(listOf("e2"), byUsd.single().rows.map { it.id })
    }

    // --- ViewModel ---

    private fun CoroutineScope.observe(vm: TransactionsViewModel) = launch { vm.uiState.collect {} }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `viewmodel groups seeded transactions and reacts to the account filter`() = runTest(dispatcher) {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
        val categoryDao = FakeCategoryDao()
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        accountDao.insert(AccountEntity("bank", "Банк", "RUB", AccountKind.CARD, 0, 0))
        categoryDao.insert(CategoryEntity("food", "Еда", CategoryKind.EXPENSE, color = 0))

        val txns = TxnRepository(txnDao, accountDao)
        txns.addExpense("cash", Money(Minor(500_00), rub), "food", d1, null)
        txns.addIncome("bank", Money(Minor(900_00), rub), null, d1, null)

        val vm = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(categoryDao), CurrencyRepository(currencyDao),
        )
        backgroundScope.observe(vm)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sections.single().rows.size)

        vm.setAccountFilter("cash")
        advanceUntilIdle()
        assertEquals(listOf("Еда"), vm.uiState.value.sections.single().rows.map { it.title })

        vm.setAccountFilter(null)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sections.single().rows.size)
    }
}
