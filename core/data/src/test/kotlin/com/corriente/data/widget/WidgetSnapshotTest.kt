package com.corriente.data.widget

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** T4.1: построитель снимка виджета — чистая функция, суммы форматируются строками (I-1). */
class WidgetSnapshotTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val rubMeta = Currency(rub, 2, 2, "₽")
    private val usdMeta = Currency(usd, 2, 2, "$")
    private val today = LocalDate.of(2026, 8, 31)

    private fun account(
        id: String,
        currency: CurrencyCode,
        opening: Long,
        includeInTotal: Boolean = true,
        archived: Boolean = false,
    ) = Account(
        id = id, name = id, currency = currency, kind = AccountKind.CASH,
        openingBalance = Money(Minor(opening), currency), color = 0, icon = null,
        displayOrder = 0, isArchived = archived, includeInTotal = includeInTotal,
    )

    private fun category(id: String, archived: Boolean = false) = Category(
        id = id, name = "cat-$id", kind = CategoryKind.EXPENSE, parentId = null,
        color = 7, icon = "🍔", origin = CategoryOrigin.USER, displayOrder = 0, isArchived = archived,
    )

    private fun expense(account: String, minor: Long, categoryId: String?, date: LocalDate) =
        Txn.Expense("e-$account-$minor-$date", date, 0, 0, account, Money(Minor(minor), rub), categoryId)

    @Test
    fun `balances are summed per pinned currency, only include_in_total accounts`() {
        val snapshot = buildWidgetSnapshot(
            accounts = listOf(
                account("cash", rub, 100_000),
                account("jar", rub, 50_000, includeInTotal = false),
                account("dollars", usd, 90_000),
            ),
            transactions = listOf(expense("cash", 25_000, null, today)),
            currencies = listOf(rubMeta, usdMeta),
            categories = emptyList(),
            pinnedCurrencies = listOf(rub, usd),
            activeAccountId = "cash",
            today = today,
            computedAt = 123L,
        )

        assertEquals(
            listOf(CurrencyLine("RUB", "750.00 ₽"), CurrencyLine("USD", "900.00 $")),
            snapshot.balances,
        )
        assertEquals("cash", snapshot.activeAccountId)
        assertEquals(123L, snapshot.computedAt)
    }

    @Test
    fun `a pinned currency with no active account produces no balance line`() {
        val snapshot = buildWidgetSnapshot(
            accounts = listOf(account("cash", rub, 0)),
            transactions = emptyList(),
            currencies = listOf(rubMeta, usdMeta),
            categories = emptyList(),
            pinnedCurrencies = listOf(rub, usd),
            activeAccountId = "cash",
            today = today,
            computedAt = 0L,
        )
        assertEquals(listOf("RUB"), snapshot.balances.map { it.code })
    }

    @Test
    fun `month expenses count only the current month, zero line when none`() {
        val snapshot = buildWidgetSnapshot(
            accounts = listOf(account("cash", rub, 0)),
            transactions = listOf(
                expense("cash", 30_000, null, LocalDate.of(2026, 8, 3)),
                expense("cash", 12_000, null, LocalDate.of(2026, 8, 20)),
                expense("cash", 99_999, null, LocalDate.of(2026, 7, 31)), // прошлый месяц
            ),
            currencies = listOf(rubMeta),
            categories = emptyList(),
            pinnedCurrencies = listOf(rub),
            activeAccountId = "cash",
            today = today,
            computedAt = 0L,
        )
        assertEquals(listOf(CurrencyLine("RUB", "420.00 ₽")), snapshot.monthExpenses)
    }

    @Test
    fun `default pinned currencies are the recently used ones, capped at three`() {
        val accounts = listOf(
            account("rub", rub, 0), account("usd", usd, 0),
            account("eur", CurrencyCode("EUR"), 0), account("gbp", CurrencyCode("GBP"), 0),
        )
        val txns = listOf(
            expense("usd", 100, null, today.minusDays(1)),
            expense("usd", 100, null, today.minusDays(2)),
            expense("rub", 100, null, today.minusDays(3)),
            expense("eur", 100, null, today.minusDays(40)), // вне окна
        )
        assertEquals(
            listOf(usd, rub),
            defaultPinnedCurrencies(accounts, txns, today),
        )
    }

    @Test
    fun `default pinned currencies fall back to active account currencies when nothing recent`() {
        val accounts = listOf(account("rub", rub, 0), account("usd", usd, 0))
        assertEquals(listOf(rub, usd), defaultPinnedCurrencies(accounts, emptyList(), today))
    }

    @Test
    fun `quick categories are the most frequent within the window, capped and filtered`() {
        val cats = (1..8).map { category("c$it") } + category("old", archived = true)
        val txns = buildList {
            // c1: 4, c2: 3, c3: 2, c4..c7: 1 each  → top 6 = c1,c2,c3,c4,c5,c6
            repeat(4) { add(expense("cash", 100, "c1", today.minusDays(1))) }
            repeat(3) { add(expense("cash", 100, "c2", today.minusDays(2))) }
            repeat(2) { add(expense("cash", 100, "c3", today.minusDays(3))) }
            add(expense("cash", 100, "c4", today.minusDays(4)))
            add(expense("cash", 100, "c5", today.minusDays(5)))
            add(expense("cash", 100, "c6", today.minusDays(6)))
            add(expense("cash", 100, "c7", today.minusDays(7)))
            add(expense("cash", 100, "c8", today.minusDays(8)))
            // вне окна — не считается
            repeat(9) { add(expense("cash", 100, "c8", today.minusDays(90))) }
        }
        val snapshot = buildWidgetSnapshot(
            accounts = listOf(account("cash", rub, 0)),
            transactions = txns,
            currencies = listOf(rubMeta),
            categories = cats,
            pinnedCurrencies = listOf(rub),
            activeAccountId = "cash",
            today = today,
            computedAt = 0L,
        )
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5", "c6"), snapshot.quickCategories.map { it.id })
        assertEquals(6, snapshot.quickCategories.size)
        assertEquals("🍔", snapshot.quickCategories.first().icon)
    }
}
