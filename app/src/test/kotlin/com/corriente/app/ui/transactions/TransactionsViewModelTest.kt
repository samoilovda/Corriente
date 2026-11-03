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
        assertEquals(listOf("1\u00A0500.00\u00A0₽"), sections.first().totals)
        assertEquals(listOf("-20.00\u00A0$"), sections.last().totals)
        // строки: расход со знаком минус, доход со знаком плюс, перевод — обе суммы
        val d2Rows = sections.first().rows.associateBy { it.id }
        assertEquals("-1\u00A0500.00\u00A0₽", d2Rows.getValue("e1").amountText)
        assertEquals("+3\u00A0000.00\u00A0₽", d2Rows.getValue("i1").amountText)
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
        assertEquals(listOf("1\u00A0750.00\u00A0₽"), sections.single().totals)
    }

    @Test
    fun `day total keeps currencies apart`() {
        val sections = buildDaySections(
            txns = listOf(expense("e1", d1, 100_00, rub, null), expense("e2", d1, 5_00, usd, null)),
            filter = TxnFilter(), accountNames = emptyMap(), categoryNames = emptyMap(),
            currenciesByCode = currenciesByCode,
        )
        assertEquals(listOf("-100.00\u00A0₽", "-5.00\u00A0$"), sections.single().totals)
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
            today = { d2.plusDays(1) },
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

    @Test
    fun `start account follows the setting - last transaction, first in the list or pinned`() = runTest(dispatcher) {
        suspend fun filterAtStart(setting: com.corriente.data.usecase.StartAccountSetting): String? {
            val txnDao = FakeTxnDao()
            val accountDao = FakeAccountDao()
            val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
            accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
            accountDao.insert(AccountEntity("bank", "Банк", "RUB", AccountKind.CARD, 0, 0))
            val txns = TxnRepository(txnDao, accountDao)
            txns.addIncome("cash", Money(Minor(100_00), rub), null, d1, null)
            Thread.sleep(3) // createdAt — миллисекунды: «последняя» операция должна отличаться по времени записи
            txns.addIncome("bank", Money(Minor(200_00), rub), null, d1, null)
            val vm = TransactionsViewModel(
                txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()), CurrencyRepository(currencyDao),
                today = { d2.plusDays(1) },
                startAccountSettings = com.corriente.data.usecase.InMemoryStartAccountSettings(setting),
            )
            backgroundScope.observe(vm)
            advanceUntilIdle()
            return vm.uiState.value.filter.accountId
        }
        assertEquals("bank", filterAtStart(com.corriente.data.usecase.StartAccountSetting(com.corriente.data.usecase.StartAccountMode.LAST_TRANSACTION)))
        assertEquals(null, filterAtStart(com.corriente.data.usecase.StartAccountSetting(com.corriente.data.usecase.StartAccountMode.FIRST)))
        assertEquals("cash", filterAtStart(com.corriente.data.usecase.StartAccountSetting(com.corriente.data.usecase.StartAccountMode.PINNED, "cash")))
    }

    @Test
    fun `start currency, most used account and remembered selection reach the screen state`() = runTest(dispatcher) {
        val accountDao = FakeAccountDao()
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        accountDao.insert(AccountEntity("usd", "Доллары", "USD", AccountKind.CASH, 0, 0))
        val txns = TxnRepository(FakeTxnDao(), accountDao)
        val usd = CurrencyCode("USD")
        // cash — три операции за последние дни, usd — одна и самая свежая.
        repeat(3) { txns.addIncome("cash", Money(Minor(10_00), rub), null, d2, null); Thread.sleep(2) }
        txns.addIncome("usd", Money(Minor(5_00), usd), null, d2, null)

        fun viewModel(settings: com.corriente.data.usecase.StartAccountSettings) = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0), CurrencyEntity("USD", 2, 2, "$", true, 1)))),
            today = { d2 }, startAccountSettings = settings,
        )
        fun settings(mode: com.corriente.data.usecase.StartAccountMode) =
            com.corriente.data.usecase.InMemoryStartAccountSettings(com.corriente.data.usecase.StartAccountSetting(mode))

        val currencyVm = viewModel(settings(com.corriente.data.usecase.StartAccountMode.LAST_CURRENCY))
        backgroundScope.observe(currencyVm)
        advanceUntilIdle()
        assertEquals("USD", currencyVm.startCurrency.value)
        assertEquals(null, currencyVm.uiState.value.filter.accountId)
        currencyVm.consumeStartCurrency()
        assertEquals(null, currencyVm.startCurrency.value)

        val frequentVm = viewModel(settings(com.corriente.data.usecase.StartAccountMode.MOST_USED))
        backgroundScope.observe(frequentVm)
        advanceUntilIdle()
        assertEquals("cash", frequentVm.uiState.value.filter.accountId)

        // Выбор в шапке запоминается; следующий запуск в режиме «как оставил» восстанавливает его.
        val remembering = settings(com.corriente.data.usecase.StartAccountMode.LAST_SELECTED)
        val first = viewModel(remembering)
        backgroundScope.observe(first)
        advanceUntilIdle()
        first.setAccountFilter("cash")
        advanceUntilIdle()
        val second = viewModel(remembering)
        backgroundScope.observe(second)
        advanceUntilIdle()
        assertEquals("cash", second.uiState.value.filter.accountId)

        // Снятый чипом счёт запоминается как «Все счета» в его валюте.
        first.setAccountFilter("usd")
        first.setAccountFilter(null)
        assertEquals(com.corriente.data.usecase.StartSelection(currency = "USD"), remembering.state.value.lastSelection)
        first.setAccountFilter("cash")
        first.clearFilters()
        assertEquals(com.corriente.data.usecase.StartSelection(currency = "RUB"), remembering.state.value.lastSelection)

        first.setAccountFilter(null)
        first.rememberAllAccounts("USD")
        val third = viewModel(remembering)
        backgroundScope.observe(third)
        advanceUntilIdle()
        assertEquals(null, third.uiState.value.filter.accountId)
        assertEquals("USD", third.startCurrency.value)
    }

    // U01: порог суммы в RUB не переинтерпретируется после выбора JPY-счёта; снятие счёта порог сохраняет.
    @Test
    fun `switching to an account in another currency clears the amount threshold with a message`() = runTest(dispatcher) {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(
            listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0), CurrencyEntity("JPY", 0, 0, "¥", true, 0)),
        )
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        accountDao.insert(AccountEntity("yen", "Иены", "JPY", AccountKind.CASH, 0, 0))
        val txns = TxnRepository(txnDao, accountDao)
        txns.addExpense("cash", Money(Minor(125_50), rub), null, d1, null)
        txns.addExpense("cash", Money(Minor(10_00), rub), null, d1, null)
        txns.addExpense("yen", Money(Minor(125), CurrencyCode("JPY")), null, d1, null)
        val vm = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(currencyDao), today = { d2 },
        )
        backgroundScope.observe(vm)
        advanceUntilIdle()
        fun shown() = vm.uiState.value.sections.flatMap { it.rows }.map { it.amountPlain }.sorted()

        val draft = TxnFilterDraft(accountId = "cash", minText = "125.50")
        vm.applyFilter((draft.validate(vm.uiState.value.filter, vm.uiState.value.accountCurrencies, vm.uiState.value.currencies) as FilterDraftCheck.Valid).filter)
        advanceUntilIdle()
        assertEquals(listOf("-125.50"), shown())

        // Ошибочный черновик не применяется — список прежний.
        val broken = TxnFilterDraft(accountId = "cash", minText = "125.505")
        assertTrue(broken.validate(vm.uiState.value.filter, vm.uiState.value.accountCurrencies, vm.uiState.value.currencies) is FilterDraftCheck.Invalid)
        assertEquals(listOf("-125.50"), shown())

        vm.setAccountFilter(null)
        advanceUntilIdle()
        assertEquals(AmountRange("RUB", 125_50, null), vm.uiState.value.filter.amount)
        assertEquals(null, vm.messages.value)

        vm.setAccountFilter("yen")
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.filter.amount)
        assertEquals(com.corriente.app.R.string.txn_filter_amount_reset, vm.messages.value?.resId)
        assertEquals(listOf("-125"), shown())
    }

    // UI-3.2: свайп-удаление + «Отменить» возвращает ту же строку (id, createdAt, updatedAt, заметка).
    @Test
    fun `swipe delete then undo restores the identical transaction`() = runTest(dispatcher) {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        val txns = TxnRepository(txnDao, accountDao)
        txns.addExpense("cash", Money(Minor(123_45), rub), null, d1, "заметка")
        val before = txnDao.rows.value.single()

        val vm = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(currencyDao), today = { d2 },
        )
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.deleteTxn(before.id)
        advanceUntilIdle()
        assertTrue(txnDao.rows.value.isEmpty())
        assertEquals(before.id, vm.pendingUndo.value?.id)

        vm.undoDelete()
        advanceUntilIdle()
        assertEquals(before, txnDao.rows.value.single())
        assertEquals(null, vm.pendingUndo.value)
    }

    @Test
    fun `swipe delete without undo is final once the snackbar expires`() = runTest(dispatcher) {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        val txns = TxnRepository(txnDao, accountDao)
        txns.addExpense("cash", Money(Minor(10_00), rub), null, d1, null)
        val id = txnDao.rows.value.single().id

        val vm = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(currencyDao), today = { d2 },
        )
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.deleteTxn(id); advanceUntilIdle()
        vm.consumeUndo()
        vm.undoDelete(); advanceUntilIdle() // нечего возвращать
        assertTrue(txnDao.rows.value.isEmpty())
    }

    // A1.1: «Показать ещё» есть, пока за границей окна лежат операции; нет операции ровно на границе.
    @Test
    fun `load earlier is offered while history remains beyond the window and vanishes once exhausted`() =
        runTest(dispatcher) {
            val txnDao = FakeTxnDao()
            val accountDao = FakeAccountDao()
            val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
            accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
            val txns = TxnRepository(txnDao, accountDao)
            val today = LocalDate.of(2026, 9, 15)
            txns.addExpense("cash", Money(Minor(100_00), rub), null, today.minusDays(3), null)
            txns.addExpense("cash", Money(Minor(200_00), rub), null, today.minusMonths(5).minusDays(2), null)

            val vm = TransactionsViewModel(
                txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
                CurrencyRepository(currencyDao), today = { today },
            )
            backgroundScope.observe(vm)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.canLoadEarlier)
            assertEquals(1, vm.uiState.value.sections.size)

            vm.loadEarlier(); advanceUntilIdle() // 6 месяцев — старая операция в окне
            assertEquals(2, vm.uiState.value.sections.size)
            assertEquals(false, vm.uiState.value.canLoadEarlier)
        }

    // Кольцо ушло на месяц глубже окна — окно расширяется шагами по 3 месяца и операции месяца видны в списке.
    @Test
    fun `ensureWindowCovers loads history down to the month shown in the ring`() = runTest(dispatcher) {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val currencyDao = FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        val txns = TxnRepository(txnDao, accountDao)
        val today = LocalDate.of(2026, 9, 15)
        txns.addExpense("cash", Money(Minor(100_00), rub), null, today.minusDays(3), null)
        txns.addExpense("cash", Money(Minor(200_00), rub), null, LocalDate.of(2025, 3, 10), null)

        val vm = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(currencyDao), today = { today },
        )
        backgroundScope.observe(vm)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.sections.size)

        vm.ensureWindowCovers(java.time.YearMonth.of(2025, 3)); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sections.size)

        // Месяц внутри окна окно не трогает.
        vm.ensureWindowCovers(java.time.YearMonth.of(2026, 9)); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sections.size)
    }

    // A2.1: экспорт CSV — ошибка провайдера даёт сообщение, а не падение; успех — «Сохранено».
    private fun exportVm(): TransactionsViewModel {
        val accountDao = FakeAccountDao()
        return TransactionsViewModel(
            TxnRepository(FakeTxnDao(), accountDao), AccountRepository(accountDao),
            CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))),
            today = { d2 }, io = dispatcher,
        )
    }

    @Test
    fun `csv export reports a failed stream as a message instead of crashing`() = runTest(dispatcher) {
        val vm = exportVm()
        backgroundScope.launch { vm.messages.collect {} }

        vm.exportCsvTo { throw java.io.FileNotFoundException("gone") }
        advanceUntilIdle()

        assertEquals(com.corriente.app.R.string.file_save_failed, vm.messages.value?.resId)
    }

    @Test
    fun `csv export writes the file and confirms`() = runTest(dispatcher) {
        val vm = exportVm()
        backgroundScope.launch { vm.messages.collect {} }
        val out = java.io.ByteArrayOutputStream()

        vm.exportCsvTo { out }
        advanceUntilIdle()

        assertTrue(out.size() > 0)
        assertEquals(com.corriente.app.R.string.file_saved, vm.messages.value?.resId)
    }

    // A2.7: операция «на следующую неделю» видна в списке сверху (раньше верхняя граница была «завтра»).
    @Test
    fun `a transaction dated a week ahead is listed on top`() = runTest(dispatcher) {
        val accountDao = FakeAccountDao()
        accountDao.insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        val txns = TxnRepository(FakeTxnDao(), accountDao)
        val today = LocalDate.of(2026, 9, 15)
        txns.addExpense("cash", Money(Minor(100_00), rub), null, today.minusDays(1), null)
        txns.addExpense("cash", Money(Minor(200_00), rub), null, today.plusDays(7), null)

        val vm = TransactionsViewModel(
            txns, AccountRepository(accountDao), CategoryRepository(FakeCategoryDao()),
            CurrencyRepository(FakeCurrencyDao(listOf(CurrencyEntity("RUB", 2, 2, "₽", true, 0)))),
            today = { today },
        )
        backgroundScope.observe(vm)
        advanceUntilIdle()

        assertEquals(listOf(today.plusDays(7), today.minusDays(1)), vm.uiState.value.sections.map { it.date })
    }
}
