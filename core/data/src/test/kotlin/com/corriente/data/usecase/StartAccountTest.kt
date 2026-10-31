package com.corriente.data.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartAccountTest {

    private val active = listOf("a", "b", "c")

    @Test
    fun `last transaction mode opens the account of the newest transaction`() {
        val setting = StartAccountSetting(StartAccountMode.LAST_TRANSACTION)
        assertEquals("c", resolveStartAccountId(setting, active, recentAccountIds = listOf("c", "a", "a")))
    }

    @Test
    fun `last transaction mode skips archived accounts and falls back to nothing without transactions`() {
        val setting = StartAccountSetting(StartAccountMode.LAST_TRANSACTION)
        assertEquals("b", resolveStartAccountId(setting, active, recentAccountIds = listOf("archived", "b")))
        assertNull(resolveStartAccountId(setting, active, recentAccountIds = emptyList()))
        assertNull(resolveStartAccountId(setting, active, recentAccountIds = listOf("archived")))
    }

    @Test
    fun `first mode selects nothing`() {
        val setting = StartAccountSetting(StartAccountMode.FIRST)
        assertNull(resolveStartAccountId(setting, active, recentAccountIds = listOf("c")))
    }

    @Test
    fun `pinned mode wins over recent transactions`() {
        val setting = StartAccountSetting(StartAccountMode.PINNED, pinnedAccountId = "b")
        assertEquals("b", resolveStartAccountId(setting, active, recentAccountIds = listOf("c")))
    }

    @Test
    fun `pinned account that is gone falls back to the last transaction`() {
        val setting = StartAccountSetting(StartAccountMode.PINNED, pinnedAccountId = "deleted")
        assertEquals("c", resolveStartAccountId(setting, active, recentAccountIds = listOf("c")))
        assertNull(resolveStartAccountId(StartAccountSetting(StartAccountMode.PINNED), active, emptyList()))
    }
}
