package com.corriente.app

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.categories.FakeCategoryDao
import com.corriente.app.ui.currencies.FakeCurrencyDao
import com.corriente.app.ui.report.PeriodMode
import com.corriente.app.ui.report.ReportViewModel
import com.corriente.app.ui.report.periodRange
import com.corriente.app.ui.transactions.TransactionsViewModel
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
import com.corriente.data.usecase.AccountBalanceUseCase
import com.corriente.data.usecase.ReportKind
import com.corriente.data.usecase.categoryReport
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.DealRate
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/**
 * Приёмка этапа 2 (BUILD_PLAN §4) и T2.3: перевод 8 695 ₽ → 100 $, сделанный ВНУТРИ отчётного
 * периода, меняет оба баланса, показывает курс 86.95 и не попадает ни в доходы/расходы,
 * ни в отчёт по категориям, ни в итог дня.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Stage2AcceptanceTest {

    private val dispatcher = StandardTestDispatcher()
    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val march = LocalDate.of(2026, 3, 15)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `transfer inside the reporting period changes balances but stays out of expenses, report and day totals`() =
        runTest(dispatcher) {
            val txnDao = FakeTxnDao()
            val accountDao = FakeAccountDao()
            val currencyDao = FakeCurrencyDao(
                listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0), CurrencyEntity("USD", 2, 2, "$", true, 1)),
            )
            val categoryDao = FakeCategoryDao()
            accountDao.insert(AccountEntity("rub", "Рубли", "RUB", AccountKind.CASH, 100_000_00, 0))
            accountDao.insert(AccountEntity("usd", "Доллары", "USD", AccountKind.CASH, 0, 0))
            categoryDao.insert(CategoryEntity("food", "Еда", CategoryKind.EXPENSE, color = 0))

            val txns = TxnRepository(txnDao, accountDao)
            val accounts = AccountRepository(accountDao)
            // расход в том же месяце — чтобы доказать, что исключается именно перевод
            txns.addExpense("rub", Money(Minor(1_000_00), rub), "food", march, null)
            txns.addTransfer("rub", Money(Minor(8_695_00), rub), "usd", Money(Minor(100_00), usd), march, "обмен")

            // 1. оба баланса изменились
            val balances = AccountBalanceUseCase(accounts, txns).observeBalances().first().associateBy { it.account.id }
            assertEquals(Money(Minor(90_305_00), rub), balances.getValue("rub").balance) // 100000 − 1000 − 8695
            assertEquals(Money(Minor(100_00), usd), balances.getValue("usd").balance)

            // 2. курс сделки
            val usdC = Currency(usd, 2, 2, "$")
            val rubC = Currency(rub, 2, 2, "₽")
            assertEquals(
                "1 USD = 86.95 RUB",
                DealRate.format(Money(Minor(100_00), usd), usdC, Money(Minor(8_695_00), rub), rubC),
            )

            // 3. отчёт по категориям за март — только расход, перевода нет
            val period = periodRange(PeriodMode.MONTH, march)
            val report = categoryReport(txns.observeAll().first(), rub, period, ReportKind.EXPENSE)
            assertEquals(listOf("food"), report.map { it.categoryId })
            assertEquals(Money(Minor(1_000_00), rub), report.single().total)

            // 3b. то же через ReportViewModel
            val reportVm = ReportViewModel(txns, CategoryRepository(categoryDao), CurrencyRepository(currencyDao)) { march }
            val jobs = launch { reportVm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(listOf("Еда"), reportVm.uiState.value.rows.map { it.name })
            jobs.cancel()

            // 4. итог дня в списке операций — только расход −1 000 ₽, перевод не считается
            val listVm = TransactionsViewModel(
                txns, accounts, CategoryRepository(categoryDao), CurrencyRepository(currencyDao),
                today = { march.plusDays(1) },
            )
            val j2 = launch { listVm.uiState.collect {} }
            advanceUntilIdle()
            val section = listVm.uiState.value.sections.single { it.date == march }
            assertEquals(listOf("-1 000.00 ₽"), section.totals)
            assertTrue(section.rows.any { it.isTransfer })   // перевод в списке показан
            j2.cancel()
        }
}
