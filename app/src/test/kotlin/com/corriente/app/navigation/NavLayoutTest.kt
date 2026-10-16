package com.corriente.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavLayoutTest {

    @Test
    fun `телефон — нижняя панель`() {
        assertEquals(NavLayout.BOTTOM_BAR, navLayoutFor(360))
        assertEquals(NavLayout.BOTTOM_BAR, navLayoutFor(599))
    }

    @Test
    fun `средняя ширина — рейка`() {
        assertEquals(NavLayout.RAIL, navLayoutFor(600))
        assertEquals(NavLayout.RAIL, navLayoutFor(839))
    }

    @Test
    fun `широкий экран — рейка и две панели`() {
        assertEquals(NavLayout.RAIL_TWO_PANE, navLayoutFor(840))
        assertEquals(NavLayout.RAIL_TWO_PANE, navLayoutFor(1280))
    }
}
