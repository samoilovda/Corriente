package com.corriente.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTextFormatTest {

    @Test
    fun splitMoneyDisplayWithFraction() {
        val parts = splitMoneyDisplay("-1\u00A0250.50\u00A0₽")
        assertEquals("-1\u00A0250", parts.primary)
        assertEquals(".50\u00A0₽", parts.secondary)
    }

    @Test
    fun splitMoneyDisplayWithoutFraction() {
        val parts = splitMoneyDisplay("125\u00A0¥")
        assertEquals("125\u00A0¥", parts.primary)
        assertEquals("", parts.secondary)
    }

    @Test
    fun splitMoneyDisplayThreeDecimals() {
        val parts = splitMoneyDisplay("1.234\u00A0KWD")
        assertEquals("1", parts.primary)
        assertEquals(".234\u00A0KWD", parts.secondary)
    }

    @Test
    fun splitMoneyDisplayTransfer() {
        val transferText = "1\u00A0000.00\u00A0₽ → 10.00\u00A0$"
        val parts = splitMoneyDisplay(transferText)
        assertEquals(transferText, parts.primary)
        assertEquals("", parts.secondary)
    }

    @Test
    fun splitMoneyDisplayPositive() {
        val parts = splitMoneyDisplay("+120\u00A0000.00\u00A0₽")
        assertEquals("+120\u00A0000", parts.primary)
        assertEquals(".00\u00A0₽", parts.secondary)
    }

    @Test
    fun fallbackVectorForCommonCategoryNames() {
        assertNotNull(CategoryIconCatalog.fallbackVectorForName("Продукты питания"))
        assertNotNull(CategoryIconCatalog.fallbackVectorForName("Кафе и рестораны"))
        assertNotNull(CategoryIconCatalog.fallbackVectorForName("Такси"))
        assertNotNull(CategoryIconCatalog.fallbackVectorForName("Зарплата"))
        assertNull(CategoryIconCatalog.fallbackVectorForName("Абсолютно случайное имя"))
    }
}
