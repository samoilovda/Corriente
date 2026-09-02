package com.corriente.data.imports

import org.junit.Assert.assertEquals
import org.junit.Test

/** F1.5 — сводка импорта переживает JSON round-trip, старые «{}» дают нули. */
class MonefyImportReportTest {

    @Test
    fun `encode then decode round-trips`() {
        val report = MonefyImportReport(accounts = 3, categories = 5, operations = 120, transfers = 8, reviews = 2)
        assertEquals(report, MonefyImportReport.decode(report.encode()))
    }

    @Test
    fun `legacy empty object decodes to all zeros`() {
        assertEquals(MonefyImportReport(), MonefyImportReport.decode("{}"))
    }

    @Test
    fun `garbage decodes to all zeros instead of throwing`() {
        assertEquals(MonefyImportReport(), MonefyImportReport.decode("not json"))
    }
}
