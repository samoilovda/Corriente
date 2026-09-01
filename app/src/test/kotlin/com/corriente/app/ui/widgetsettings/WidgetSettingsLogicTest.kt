package com.corriente.app.ui.widgetsettings

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Account
import com.corriente.data.widget.WidgetConfig
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** T4.4: чистая логика экрана настроек виджета. */
class WidgetSettingsLogicTest {

    private val today = LocalDate.of(2026, 8, 31)
    private fun cur(code: String) = Currency(CurrencyCode(code), 2, 2, code.first().toString())
    private fun acc(id: String, code: String) = Account(
        id, id, CurrencyCode(code), AccountKind.CASH, Money(Minor(0), CurrencyCode(code)),
        0, null, 0, isArchived = false, includeInTotal = true,
    )

    @Test
    fun `pinned toggle caps at three and removes when already pinned`() {
        assertEquals(listOf("RUB"), nextPinnedCurrencies(emptyList(), "RUB"))
        assertEquals(listOf("RUB", "USD"), nextPinnedCurrencies(listOf("RUB"), "USD"))
        assertEquals(listOf("RUB", "EUR"), nextPinnedCurrencies(listOf("RUB", "USD", "EUR"), "USD"))
        assertEquals(listOf("RUB", "USD", "EUR"), nextPinnedCurrencies(listOf("RUB", "USD", "EUR"), "GBP"))
    }

    @Test
    fun `empty config shows defaults as pinned and first account as active`() {
        val state = widgetSettingsUiState(
            currencies = listOf(cur("RUB"), cur("USD")),
            accounts = listOf(acc("a", "RUB"), acc("b", "USD")),
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
            transactions = emptyList(),
            config = WidgetConfig(pinnedCurrencyCodes = listOf("USD"), activeAccountId = "gone"),
            today = today,
        )
        assertEquals(listOf("USD"), state.currencies.filter { it.pinned }.map { it.code })
        assertEquals("a", state.accounts.single { it.active }.id)
        assertEquals(true, state.canPinMore)
    }
}
