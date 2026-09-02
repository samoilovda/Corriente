package com.corriente.app.ui.widgetsettings

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.data.widget.WidgetConfig
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** T4.4/R4.3: чистая логика экрана настроек виджета. */
class WidgetSettingsLogicTest {

    private val today = LocalDate.of(2026, 8, 31)
    private fun cur(code: String) = Currency(CurrencyCode(code), 2, 2, code.first().toString())
    private fun acc(id: String, code: String) = Account(
        id, id, CurrencyCode(code), AccountKind.CASH, Money(Minor(0), CurrencyCode(code)),
        0, null, 0, isArchived = false, includeInTotal = true,
    )
    private fun category(id: String, archived: Boolean = false) = Category(
        id = id, name = "cat-$id", kind = CategoryKind.EXPENSE, parentId = null,
        color = 0, icon = null, origin = CategoryOrigin.USER, displayOrder = 0, isArchived = archived,
    )
    private fun expense(account: String, categoryId: String, date: LocalDate) =
        Txn.Expense("e-$account-$categoryId-$date", date, 0, 0, account, Money(Minor(100), CurrencyCode("RUB")), categoryId)

    @Test
    fun `pinned toggle caps at three and removes when already pinned`() {
        assertEquals(listOf("RUB"), nextPinnedCurrencies(emptyList(), "RUB"))
        assertEquals(listOf("RUB", "USD"), nextPinnedCurrencies(listOf("RUB"), "USD"))
        assertEquals(listOf("RUB", "EUR"), nextPinnedCurrencies(listOf("RUB", "USD", "EUR"), "USD"))
        assertEquals(listOf("RUB", "USD", "EUR"), nextPinnedCurrencies(listOf("RUB", "USD", "EUR"), "GBP"))
    }

    // R4.3 — тот же кап/тоггл, но для категорий (до 6).
    @Test
    fun `pinned category toggle caps at six and removes when already pinned`() {
        val five = listOf("c1", "c2", "c3", "c4", "c5")
        assertEquals(listOf("c1"), nextPinnedCategories(emptyList(), "c1"))
        assertEquals(five + "c6", nextPinnedCategories(five, "c6"))
        assertEquals(five + "c6", nextPinnedCategories(five + "c6", "c7")) // уже 6 — не добавляется
        assertEquals(listOf("c1", "c3"), nextPinnedCategories(listOf("c1", "c2", "c3"), "c2"))
    }

    @Test
    fun `empty config shows defaults as pinned and first account as active`() {
        val state = widgetSettingsUiState(
            currencies = listOf(cur("RUB"), cur("USD")),
            accounts = listOf(acc("a", "RUB"), acc("b", "USD")),
            categories = emptyList(),
            transactions = emptyList(),
            config = WidgetConfig(),
            today = today,
        )
        assertEquals(setOf("RUB", "USD"), state.currencies.filter { it.pinned }.map { it.code }.toSet())
        assertEquals("a", state.accounts.single { it.active }.id)
        assertEquals(2, state.pinnedCount)
    }

    @Test
    fun `explicit config wins and stale active account falls back to first`() {
        val state = widgetSettingsUiState(
            currencies = listOf(cur("RUB"), cur("USD")),
            accounts = listOf(acc("a", "RUB"), acc("b", "USD")),
            categories = emptyList(),
            transactions = emptyList(),
            config = WidgetConfig(pinnedCurrencyCodes = listOf("USD"), activeAccountId = "gone"),
            today = today,
        )
        assertEquals(listOf("USD"), state.currencies.filter { it.pinned }.map { it.code })
        assertEquals("a", state.accounts.single { it.active }.id)
        assertEquals(true, state.canPinMore)
    }

    // R4.3 — без явного закрепления экран показывает тот же автоподбор, что и виджет.
    @Test
    fun `empty category config shows the automatic frequent selection as pinned`() {
        val cats = listOf(category("c1"), category("c2"))
        val state = widgetSettingsUiState(
            currencies = emptyList(),
            accounts = listOf(acc("a", "RUB")),
            categories = cats,
            transactions = listOf(
                expense("a", "c1", today.minusDays(1)),
                expense("a", "c1", today.minusDays(2)),
                expense("a", "c2", today.minusDays(3)),
            ),
            config = WidgetConfig(),
            today = today,
        )
        assertEquals(setOf("c1", "c2"), state.categories.filter { it.pinned }.map { it.id }.toSet())
        assertEquals(2, state.pinnedCategoryCount)
        assertEquals(true, state.canPinMoreCategories)
    }

    @Test
    fun `explicit pinned categories win over the automatic selection`() {
        val cats = listOf(category("c1"), category("c2"))
        val state = widgetSettingsUiState(
            currencies = emptyList(),
            accounts = listOf(acc("a", "RUB")),
            categories = cats,
            transactions = listOf(expense("a", "c1", today.minusDays(1))), // c1 был бы автоподбором
            config = WidgetConfig(pinnedCategoryIds = listOf("c2")),
            today = today,
        )
        assertEquals(listOf("c2"), state.categories.filter { it.pinned }.map { it.id })
    }
}
