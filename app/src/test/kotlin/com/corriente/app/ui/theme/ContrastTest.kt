package com.corriente.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    @Test
    fun `чёрное на белом даёт 21`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun `порядок аргументов не важен`() {
        assertEquals(contrastRatio(Color.Red, Color.White), contrastRatio(Color.White, Color.Red), 0.0001f)
    }

    @Test
    fun `доход и расход читаются на поверхностях обеих тем`() {
        val cases = listOf(
            "light" to (LightMoneyColors to CorrienteLightColors),
            "dark" to (DarkMoneyColors to CorrienteDarkColors),
        )
        for ((name, pair) in cases) {
            val (money, scheme) = pair
            for (surface in listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainerHigh)) {
                assertTrue("$name income ${contrastRatio(money.income, surface)}", contrastRatio(money.income, surface) >= 4.5f)
                assertTrue("$name expense ${contrastRatio(money.expense, surface)}", contrastRatio(money.expense, surface) >= 4.5f)
            }
        }
    }
}
