package com.corriente.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/** R2.1 — чистые функции построения MATCH/LIKE запросов поиска (без Room). */
class TxnSearchTest {

    @Test
    fun `single word becomes a quoted prefix token`() {
        assertEquals("\"кофе\"*", buildFtsPrefixQuery("кофе"))
    }

    @Test
    fun `multiple words become several prefix tokens implicitly ANDed`() {
        assertEquals("\"кофе\"* \"трав\"*", buildFtsPrefixQuery("кофе  трав"))
    }

    @Test
    fun `blank query yields empty match string`() {
        assertEquals("", buildFtsPrefixQuery("   "))
        assertEquals("", buildFtsPrefixQuery(""))
    }

    @Test
    fun `quotes inside a word are escaped by doubling`() {
        assertEquals("\"a\"\"b\"*", buildFtsPrefixQuery("a\"b"))
    }

    @Test
    fun `like pattern wraps a lowercased trimmed needle`() {
        assertEquals("%тинькофф%", buildLikePattern("  Тинькофф  "))
    }
}
