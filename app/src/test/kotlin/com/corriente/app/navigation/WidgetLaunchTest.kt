package com.corriente.app.navigation

import com.corriente.app.ui.txnentry.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLaunchTest {

    @Test
    fun `виджет открывает форму нужного вида`() {
        assertEquals(EntryKind.INCOME, widgetEntryKind("corriente://entry?kind=INCOME&from=widget"))
        assertEquals(EntryKind.EXPENSE, widgetEntryKind("corriente://entry?kind=EXPENSE&from=widget"))
        assertEquals(EntryKind.EXPENSE, widgetEntryKind("corriente://entry?from=widget&kind=EXPENSE"))
    }

    @Test
    fun `неизвестный kind с виджета — расход`() {
        assertEquals(EntryKind.EXPENSE, widgetEntryKind("corriente://entry?kind=BOGUS&from=widget"))
    }

    @Test
    fun `ярлык приложения и обычный запуск — не виджет`() {
        assertNull(widgetEntryKind("corriente://entry?kind=EXPENSE"))
        assertNull(widgetEntryKind("corriente://transfer"))
        assertNull(widgetEntryKind(null))
    }
}
