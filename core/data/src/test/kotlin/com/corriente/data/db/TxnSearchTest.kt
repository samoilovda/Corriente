package com.corriente.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R2.1 — чистые функции поиска: MATCH-запрос и сравнение названий (без Room). */
class TxnSearchTest {

    @Test
    fun `single word becomes a quoted prefix token`() {
        assertEquals("\"кофе*\"", buildFtsPrefixQuery("кофе"))
    }

    @Test
    fun `multiple words become several prefix tokens implicitly ANDed`() {
        assertEquals("\"кофе*\" \"трав*\"", buildFtsPrefixQuery("кофе  трав"))
    }

    @Test
    fun `words are lowercased so the quoted prefix token matches the folded index`() {
        assertEquals("\"молок*\"", buildFtsPrefixQuery("МОЛОК"))
    }

    @Test
    fun `blank query yields empty match string`() {
        assertEquals("", buildFtsPrefixQuery("   "))
        assertEquals("", buildFtsPrefixQuery(""))
    }

    @Test
    fun `quotes inside a word are escaped by doubling`() {
        assertEquals("\"a\"\"b*\"", buildFtsPrefixQuery("a\"b"))
    }

    // W11: регистр кириллицы сворачивается в Kotlin — SQLite без ICU (десктоп) этого не умеет.
    @Test
    fun `name matching ignores case of cyrillic and trims the query`() {
        assertTrue(nameMatchesQuery("Тинькофф", "  тинькофф  "))
        assertTrue(nameMatchesQuery("Продукты", "ПРОДУК"))
        assertTrue(nameMatchesQuery("Tinkoff", "tink"))
        assertFalse(nameMatchesQuery("Наличные", "карта"))
        assertFalse(nameMatchesQuery("Наличные", "   "))
    }
}
