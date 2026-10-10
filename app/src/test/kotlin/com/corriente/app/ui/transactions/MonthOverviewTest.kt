package com.corriente.app.ui.transactions

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.usecase.AccountBalance
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthOverviewTest {

    private val uzs = CurrencyCode("UZS")
    private val usd = CurrencyCode("USD")
    private val currencies = listOf(Currency(uzs, 2, 2, "UZS"), Currency(usd, 2, 2, "USD"))
    private val month = YearMonth.of(2026, 10)
    private val day = LocalDate.of(2026, 10, 5)

    private fun money(raw: Long, c: CurrencyCode = uzs) = Money(Minor(raw), c)

    private fun account(id: String, c: CurrencyCode, order: Int = 0, inTotal: Boolean = true) =
        Account(id, id, c, AccountKind.CASH, money(0, c), 0, null, order, isArchived = false, includeInTotal = inTotal)

    private fun cat(id: String, order: Int, parent: String? = null, kind: CategoryKind = CategoryKind.EXPENSE) =
        Category(id, id, kind, parent, 0xFF112233.toInt(), null, CategoryOrigin.USER, order, isArchived = false)

    private fun exp(acc: String, raw: Long, cat: String?, c: CurrencyCode = uzs) =
        Txn.Expense("e$raw$acc$cat", day, 0, 0, acc, money(raw, c), cat)

    private fun inc(acc: String, raw: Long, c: CurrencyCode = uzs) = Txn.Income("i$raw$acc", day, 0, 0, acc, money(raw, c), null)

    private val accounts = listOf(account("cash", uzs), account("card", uzs, 1), account("dollars", usd, 2))
    private val categories = listOf(cat("food", 0), cat("snacks", 1, parent = "food"), cat("home", 2), cat("taxi", 3))

    private fun build(txns: List<Txn>, balances: List<AccountBalance> = emptyList(), account: String? = null, currency: String? = null) =
        buildMonthOverview(month, txns, accounts, categories, currencies, balances, account, currency)!!

    @Test
    fun allAccountsInOneCurrencyFoldSubcategoriesAndSplitPercents() {
        val o = build(
            listOf(
                exp("cash", 300_00, "food"), exp("card", 100_00, "snacks"), // еда = 400 (подкатегория свёрнута)
                exp("cash", 600_00, "home"), exp("dollars", 999_00, "taxi", usd), // USD в шапку UZS не попадает
                inc("card", 5_000_00), Txn.Transfer("t", day, 0, 0, "cash", money(1_00), "card", money(1_00)),
            ),
        )
        assertEquals("UZS", o.currencyCode)
        assertNull(o.accountId)
        assertEquals(listOf(600_00L, 400_00L), o.slices.map { it.valueMinor })
        assertEquals(mapOf("food" to 40, "home" to 60, "taxi" to null), o.icons.associate { it.categoryId to it.percent })
        assertTrue(o.icons.none { it.categoryId == "snacks" }) // вокруг кольца — только верхний уровень
        assertTrue(o.expenseText.startsWith("1 000.00"))
        assertTrue(o.incomeText.startsWith("5 000.00"))
    }

    @Test
    fun selectedAccountLimitsAmountsAndShowsItsBalance() {
        val balances = listOf(
            AccountBalance(accounts[0], money(-50_00)),
            AccountBalance(accounts[1], money(700_00)),
        )
        val o = build(listOf(exp("cash", 300_00, "food"), exp("card", 100_00, "home")), balances, account = "cash")
        assertEquals("cash", o.accountId)
        assertEquals(listOf(300_00L), o.slices.map { it.valueMinor })
        assertTrue(o.balanceNegative)
        // «Все счета» в UZS — сумма балансов счетов этой валюты
        val all = build(emptyList(), balances, currency = "UZS")
        assertTrue(all.balanceText.startsWith("650.00"))
        assertTrue(all.slices.isEmpty())
    }

    @Test
    fun currencyChoiceSwitchesScopeAndNoAccountsGivesNull() {
        val o = build(listOf(exp("dollars", 12_00, "taxi", usd)), currency = "USD")
        assertEquals("USD", o.currencyCode)
        assertEquals(listOf("USD", "UZS"), o.currencyOptions.sorted())
        assertEquals(100, o.icons.first { it.categoryId == "taxi" }.percent)
        assertNull(buildMonthOverview(month, emptyList(), emptyList(), categories, currencies, emptyList(), null, null))
    }

    @Test
    fun iconsPreferSpentCategoriesWhenThereAreTooMany() {
        val many = (0 until 20).map { cat("c$it", it) }
        val o = buildMonthOverview(
            month, listOf(exp("cash", 5_00, "c19")), accounts, many, currencies, emptyList(), null, null,
        )!!
        assertEquals(OVERVIEW_MAX_ICONS, o.icons.size)
        assertTrue(o.icons.any { it.categoryId == "c19" })
        assertEquals("c19", o.icons.last().categoryId) // на экране — порядок категорий
    }

    @Test
    fun pinnedCategoriesReplaceAutomaticChoiceAndSkipUnknownIds() {
        val o = buildMonthOverview(
            month, listOf(exp("cash", 5_00, "food")), accounts, categories, currencies, emptyList(), null, null,
            pinned = listOf("taxi", "gone", "snacks"), // подкатегория и несуществующий id не попадают
        )!!
        assertEquals(listOf("taxi"), o.icons.map { it.categoryId })
        assertEquals(listOf(5_00L), o.slices.map { it.valueMinor }) // кольцо по-прежнему по всем тратам
    }
}
