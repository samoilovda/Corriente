package com.corriente.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Нижняя панель разделов показывается только на корневых маршрутах (BACKLOG: панель
 * не должна оставаться на под-экранах вроде «Валюты», открытых из «Настроек»).
 */
class CorrienteDestinationTest {

    @Test
    fun `the four bottom-nav routes are top level`() {
        CorrienteDestination.entries.forEach {
            assertTrue(it.route, CorrienteDestination.isTopLevelRoute(it.route))
        }
    }

    @Test
    fun `sub-screen routes are not top level`() {
        listOf(
            "currencies", "categories", "import_monefy", "widget_settings", "autobackup",
            "fx_report", "txn_entry?kind=EXPENSE", "txn_edit/abc", "transfer", "transfer_edit/abc",
        ).forEach {
            assertFalse(it, CorrienteDestination.isTopLevelRoute(it))
        }
    }

    @Test
    fun `null route is not top level`() {
        assertFalse(CorrienteDestination.isTopLevelRoute(null))
    }
}
