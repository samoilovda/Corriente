package com.corriente.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyToneTest {

    @Test
    fun `доход зелёный, расход обычный`() {
        assertEquals(MoneyTone.INCOME, moneyTone(MoneyKind.INCOME))
        assertEquals(MoneyTone.NEUTRAL, moneyTone(MoneyKind.EXPENSE))
    }

    @Test
    fun `перевод приглушён независимо от знака`() {
        assertEquals(MoneyTone.MUTED, moneyTone(MoneyKind.TRANSFER, isNegativeBalance = true))
    }

    @Test
    fun `остаток красный только отрицательный`() {
        assertEquals(MoneyTone.NEGATIVE, moneyTone(MoneyKind.BALANCE, isNegativeBalance = true))
        assertEquals(MoneyTone.NEUTRAL, moneyTone(MoneyKind.BALANCE, isNegativeBalance = false))
    }

    @Test
    fun `расход с флагом отрицательности остаётся обычным`() {
        assertEquals(MoneyTone.NEUTRAL, moneyTone(MoneyKind.EXPENSE, isNegativeBalance = true))
    }
}
