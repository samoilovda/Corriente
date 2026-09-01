package com.corriente.app.ui.imports

import com.corriente.data.imports.MonefyCsvParser
import com.corriente.data.imports.MonefyImportPlanner
import com.corriente.data.imports.ReviewReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T3.3: сводка dry-run над `testdata/monefy_sample.csv` строго по
 * `testdata/monefy_sample.expected.md` (вариант А).
 */
class ImportSummaryTest {

    private val summary by lazy {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        MonefyImportPlanner.plan(MonefyCsvParser.parse(csv)).toImportSummary()
    }

    @Test
    fun `dry-run counts match expected md`() {
        assertEquals(4, summary.accounts)
        assertEquals(2, summary.openingBalances)      // Cash 10 000 RUB + Wallet 80 RUB
        assertEquals(5, summary.categories)
        assertEquals(6, summary.operations)           // контрольные суммы: 6 операций
        assertEquals(5, summary.transfers)
        assertEquals(1, summary.unpairedHalves)       // непарная половинка 10/03 (расход)
    }

    @Test
    fun `three review items, one per reason`() {
        assertEquals(3, summary.reviews.size)
        assertEquals(
            setOf(
                ReviewReason.EXCESS_PRECISION,
                ReviewReason.AMBIGUOUS_PAIRING,
                ReviewReason.ANOMALOUS_CURRENCY,
            ),
            summary.reviews.map { it.reason }.toSet(),
        )
        summary.reviews.forEach { assertEquals(true, it.message.isNotBlank()) }
    }

    @Test
    fun `sample file has no unparsed rows`() {
        assertEquals(emptyList<String>(), summary.errors)
    }
}
