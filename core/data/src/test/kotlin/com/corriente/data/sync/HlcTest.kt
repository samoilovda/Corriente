package com.corriente.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    @Test
    fun tickIsMonotonicEvenWhenTheClockGoesBack() {
        var h = Hlc.zero("a").tick(1_000)
        val seen = mutableListOf(h)
        listOf(1_000L, 999L, 500L, 1_001L, 1_001L).forEach { now -> h = h.tick(now); seen += h }
        assertEquals(seen.sorted(), seen)
        assertEquals(seen.size, seen.toSet().size)
    }

    @Test
    fun localChangeAfterReceivingIsNewerThanTheRemoteOneDespiteSlowClock() {
        val remote = Hlc(5_000, 3, "phone")
        val tablet = Hlc(1_000, 0, "tablet").receive(remote, nowMs = 1_200) // часы планшета отстают
        val next = tablet.tick(1_300)
        assertTrue(next > remote)
    }

    @Test
    fun encodingSortsLikeTheClockAndRoundTrips() {
        val values = listOf(Hlc(1, 0, "b"), Hlc(1, 1, "a"), Hlc(2, 0, "a"), Hlc(1, 0, "a"), Hlc(10, 255, "z"))
        assertEquals(values.sorted().map { it.encode() }, values.map { it.encode() }.sorted())
        values.forEach { assertEquals(it, Hlc.decode(it.encode())) }
    }
}
