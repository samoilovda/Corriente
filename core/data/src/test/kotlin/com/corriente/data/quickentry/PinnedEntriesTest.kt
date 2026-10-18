package com.corriente.data.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E1 — закрепление шаблонов быстрого ввода. */
class PinnedEntriesTest {

    private fun entry(n: Int, categoryId: String? = "cat-$n") =
        FrequentEntry(FrequentEntryKind.EXPENSE, "acc", categoryId, n * 100L, "RUB")

    @Test
    fun `toggle pins then unpins, keeping pin order`() {
        val store = InMemoryPinnedEntries()
        assertTrue(store.toggle(entry(1)))
        assertTrue(store.toggle(entry(2)))
        assertEquals(listOf(entry(1), entry(2)), store.pinned.value)
        assertTrue(store.toggle(entry(1)))
        assertEquals(listOf(entry(2)), store.pinned.value)
    }

    @Test
    fun `pin beyond the limit is rejected but unpin always works`() {
        val store = InMemoryPinnedEntries((1..PINNED_ENTRIES_LIMIT).map(::entry))
        assertFalse(store.toggle(entry(99)))
        assertEquals(PINNED_ENTRIES_LIMIT, store.pinned.value.size)
        assertTrue(store.toggle(entry(1)))
        assertTrue(store.toggle(entry(99)))
    }

    @Test
    fun `merge puts pinned first and does not duplicate them among frequent`() {
        val merged = mergePinnedAndFrequent(pinned = listOf(entry(3), entry(1)), frequent = listOf(entry(1), entry(2)))
        assertEquals(
            listOf(QuickEntrySlot(entry(3), true), QuickEntrySlot(entry(1), true), QuickEntrySlot(entry(2), false)),
            merged,
        )
    }

    @Test
    fun `pinned entry stays even when it is no longer frequent`() {
        val merged = mergePinnedAndFrequent(pinned = listOf(entry(7)), frequent = emptyList())
        assertEquals(listOf(QuickEntrySlot(entry(7), true)), merged)
    }

    @Test
    fun `codec round-trips entries with and without category`() {
        for (e in listOf(entry(1), entry(2, categoryId = null), entry(3).copy(kind = FrequentEntryKind.INCOME))) {
            assertEquals(e, PinnedEntryCodec.decode(PinnedEntryCodec.encode(e)!!))
        }
    }

    @Test
    fun `codec drops garbage and ids with separators`() {
        assertNull(PinnedEntryCodec.decode(""))
        assertNull(PinnedEntryCodec.decode("EXPENSE|acc|cat|notanumber|RUB"))
        assertNull(PinnedEntryCodec.decode("TRANSFER|acc|cat|100|RUB"))
        assertNull(PinnedEntryCodec.decode("EXPENSE||cat|100|RUB"))
        assertNull(PinnedEntryCodec.encode(entry(1).copy(accountId = "a|b")))
    }
}
