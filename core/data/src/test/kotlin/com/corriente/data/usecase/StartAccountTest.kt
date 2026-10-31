package com.corriente.data.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartAccountTest {

    private val active = listOf(
        StartAccountInfo("a", "RUB"),
        StartAccountInfo("b", "RUB"),
        StartAccountInfo("c", "USD"),
    )

    private fun resolve(
        setting: StartAccountSetting,
        recent: List<String> = emptyList(),
        frequent: List<String> = emptyList(),
    ) = resolveStartSelection(setting, active, recent, frequent)

    private fun mode(m: StartAccountMode, pinned: String? = null, last: StartSelection? = null) =
        StartAccountSetting(m, pinned, last)

    @Test
    fun `last transaction opens the account of the newest transaction, skipping archived ones`() {
        val setting = mode(StartAccountMode.LAST_TRANSACTION)
        assertEquals(StartSelection(accountId = "c"), resolve(setting, recent = listOf("c", "a", "a")))
        assertEquals(StartSelection(accountId = "b"), resolve(setting, recent = listOf("archived", "b")))
        assertNull(resolve(setting, recent = emptyList()))
        assertNull(resolve(setting, recent = listOf("archived")))
    }

    @Test
    fun `first mode selects nothing`() {
        assertNull(resolve(mode(StartAccountMode.FIRST), recent = listOf("c")))
    }

    @Test
    fun `pinned account wins, a gone one falls back to the last transaction`() {
        assertEquals(StartSelection(accountId = "b"), resolve(mode(StartAccountMode.PINNED, "b"), recent = listOf("c")))
        assertEquals(StartSelection(accountId = "c"), resolve(mode(StartAccountMode.PINNED, "deleted"), recent = listOf("c")))
        assertNull(resolve(mode(StartAccountMode.PINNED), recent = emptyList()))
    }

    @Test
    fun `last currency opens all accounts in the currency of the newest transaction`() {
        val setting = mode(StartAccountMode.LAST_CURRENCY)
        assertEquals(StartSelection(currency = "USD"), resolve(setting, recent = listOf("c", "a")))
        assertEquals(StartSelection(currency = "RUB"), resolve(setting, recent = listOf("archived", "b")))
        assertNull(resolve(setting))
    }

    @Test
    fun `most used picks the most frequent active account and falls back to the last transaction`() {
        val setting = mode(StartAccountMode.MOST_USED)
        assertEquals(StartSelection(accountId = "b"), resolve(setting, recent = listOf("c"), frequent = listOf("archived", "b", "a")))
        assertEquals(StartSelection(accountId = "c"), resolve(setting, recent = listOf("c"), frequent = emptyList()))
    }

    @Test
    fun `last selected restores an account, a currency or the all-accounts choice`() {
        fun last(selection: StartSelection?) = mode(StartAccountMode.LAST_SELECTED, last = selection)
        assertEquals(StartSelection(accountId = "b"), resolve(last(StartSelection(accountId = "b")), recent = listOf("c")))
        assertEquals(StartSelection(currency = "USD"), resolve(last(StartSelection(currency = "USD")), recent = listOf("a")))
        // «Все счета» без валюты — осознанный выбор: ничего не выбираем.
        assertNull(resolve(last(StartSelection()), recent = listOf("c")))
        // Ничего не выбирал или счёта уже нет — счёт последней операции; исчезнувшая валюта — ничего.
        assertEquals(StartSelection(accountId = "c"), resolve(last(null), recent = listOf("c")))
        assertEquals(StartSelection(accountId = "c"), resolve(last(StartSelection(accountId = "gone")), recent = listOf("c")))
        assertNull(resolve(last(StartSelection(currency = "EUR")), recent = listOf("c")))
    }

    @Test
    fun `remembering a selection keeps mode and pinned account`() {
        val settings = InMemoryStartAccountSettings(StartAccountSetting(StartAccountMode.PINNED, "a"))
        settings.rememberSelection(StartSelection(currency = "USD"))
        assertEquals(StartAccountSetting(StartAccountMode.PINNED, "a", StartSelection(currency = "USD")), settings.state.value)
    }
}
