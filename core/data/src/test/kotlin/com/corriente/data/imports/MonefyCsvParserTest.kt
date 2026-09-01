package com.corriente.data.imports

import com.corriente.money.Minor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * T3.1: разбор `testdata/monefy_sample.csv` строго по `testdata/monefy_sample.expected.md`
 * и по правилам формата `docs/MONEFY_IMPORT.md` §1–2.
 */
class MonefyCsvParserTest {

    private fun sampleCsv(): String {
        // рабочая директория теста — каталог модуля (core/data); файл лежит в корне репозитория.
        val candidates = listOf(
            File("../../testdata/monefy_sample.csv"),
            File("testdata/monefy_sample.csv"),
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }

    private val result by lazy { MonefyCsvParser.parse(sampleCsv()) }

    @Test
    fun `all 19 data rows parse with no errors`() {
        assertEquals(emptyList<MonefyRowError>(), result.errors)
        assertEquals(19, result.rows.size)
    }

    @Test
    fun `comma is thousands and dot is decimal, not locale-dependent`() {
        val eatingOut = result.rows.single { it.line == 5 }
        assertEquals("Eating out", eatingOut.rawCategory)
        assertEquals(Minor(-125_075), eatingOut.amount) // "-1,250.75" RUB

        val salary = result.rows.single { it.line == 6 }
        assertEquals(Minor(5_000_000), salary.amount)   // "50,000" RUB
    }

    @Test
    fun `a category containing a comma survives the quoting`() {
        val repair = result.rows.single { it.line == 7 }
        assertEquals("Дом, ремонт", repair.rawCategory)
        assertEquals(Minor(-99_900), repair.amount)
    }

    @Test
    fun `the two currency columns are read by index, not by header name`() {
        // строка 9: сумма в USD (кол. 4), converted — в RUB (кол. 6)
        val fromCash = result.rows.single { it.line == 9 }
        assertEquals("USD", fromCash.currency.code)
        assertEquals("RUB", fromCash.baseCurrency.code)
        assertEquals(869_500L, fromCash.convertedAbs) // |"8,695" RUB|
    }

    @Test
    fun `excess fractional precision is rounded and flagged (variant A) not dropped`() {
        val row = result.rows.single { it.line == 9 } // 100.001 USD
        assertEquals(Minor(100_00), row.amount)
        assertTrue(row.amountRoundedFromExcess)
        // обычные суммы флаг не поднимают
        assertTrue(result.rows.filter { it.line != 9 }.none { it.amountRoundedFromExcess })
    }

    @Test
    fun `date is parsed as dd slash MM slash yyyy`() {
        assertEquals(LocalDate.of(2021, 3, 1), result.rows.single { it.line == 2 }.date)
        assertEquals(LocalDate.of(2021, 3, 11), result.rows.single { it.line == 20 }.date)
    }

    @Test
    fun `legitimate duplicate rows are both kept`() {
        assertEquals(2, result.rows.count { it.line in listOf(3, 4) && it.rawCategory == "Food" })
    }
}
