package com.corriente.data.imports

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T3.5: приёмка на данных. Реального экспорта в репозитории нет (личные данные), поэтому
 * контроль идёт по синтетическому `testdata/monefy_sample.csv` строго против блока
 * «Контрольные суммы» в `testdata/monefy_sample.expected.md` (docs/MONEFY_IMPORT.md §6):
 * суммы считаются по колонке `converted amount`, без переводов и начальных остатков.
 */
class MonefyControlSumsTest {

    private val rows by lazy {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        MonefyCsvParser.parse(csv).rows
    }

    private fun MonefyRow.isPseudo() = rawCategory.startsWith("Initial balance '") ||
        rawCategory.startsWith("To '") || rawCategory.startsWith("From '")

    @Test
    fun `yearly control sums match expected md`() {
        val plain = rows.filterNot { it.isPseudo() }

        val expenses = plain.filter { it.amount.raw < 0 }.sumOf { it.convertedAbs }
        val incomes = plain.filter { it.amount.raw > 0 }.sumOf { it.convertedAbs }

        assertEquals(6, plain.size)                 // Операций: 6
        assertEquals(452_975L, expenses)            // Расходы: 4 529.75 RUB
        assertEquals(5_000_000L, incomes)           // Доходы: 50 000 RUB
    }
}
