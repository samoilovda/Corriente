package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {

    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val clp = Currency(CurrencyCode("CLP"), minorUnits = 0, displayScale = 0, symbol = "$")

    @Test
    fun `empty input has no value`() {
        assertTrue(AmountInput.empty().isEmpty)
        assertNull(AmountInput.empty().toMinorOrNull(rub))
    }

    @Test
    fun `digits accumulate into the integer part`() {
        var input = AmountInput.empty()
        input = input.appendDigit('1', rub).appendDigit('2', rub).appendDigit('5', rub)
        assertEquals("125", input.displayText())
        assertEquals(Minor(12500), input.toMinorOrNull(rub))
    }

    @Test
    fun `leading zero is replaced, not accumulated`() {
        var input = AmountInput.empty()
        input = input.appendDigit('0', rub).appendDigit('5', rub)
        assertEquals("5", input.displayText())
    }

    @Test
    fun `decimal point then digits fill the fractional part`() {
        var input = AmountInput.empty()
        input = input.appendDigit('1', rub).appendDigit('2', rub)
            .appendDecimalPoint(rub).appendDigit('5', rub)
        assertEquals("12.5", input.displayText())
        assertEquals(Minor(1250), input.toMinorOrNull(rub)) // 12.50 RUB
    }

    @Test
    fun `fractional digits are capped at currency minorUnits`() {
        var input = AmountInput.empty()
        input = input.appendDigit('1', rub).appendDecimalPoint(rub)
            .appendDigit('2', rub).appendDigit('3', rub).appendDigit('4', rub) // 3rd digit ignored
        assertEquals("1.23", input.displayText())
    }

    @Test
    fun `second decimal point is ignored`() {
        var input = AmountInput.empty()
        input = input.appendDigit('1', rub).appendDecimalPoint(rub)
            .appendDecimalPoint(rub).appendDigit('5', rub)
        assertEquals("1.5", input.displayText())
    }

    // I-1/I-4 в духе: CLP не имеет минорных единиц - десятичная точка на клавиатуре не действует.
    @Test
    fun `decimal point is a no-op for a zero-minor-unit currency`() {
        var input = AmountInput.empty()
        input = input.appendDigit('5', clp).appendDecimalPoint(clp).appendDigit('0', clp)
        assertEquals("50", input.displayText())
        assertEquals(Minor(50), input.toMinorOrNull(clp))
    }

    @Test
    fun `backspace removes fraction digit, then decimal point, then integer digit`() {
        var input = AmountInput.empty()
        input = input.appendDigit('1', rub).appendDecimalPoint(rub).appendDigit('5', rub)
        assertEquals("1.5", input.displayText())

        input = input.backspace()
        assertEquals("1.", input.displayText())

        input = input.backspace()
        assertEquals("1", input.displayText())

        input = input.backspace()
        assertEquals("0", input.displayText())
        assertTrue(input.isEmpty)
    }

    @Test
    fun `backspace on empty input is a no-op`() {
        val input = AmountInput.empty().backspace()
        assertTrue(input.isEmpty)
    }

    @Test
    fun `fromText reads a plain typed amount, comma or dot as the decimal separator`() {
        assertEquals(Minor(123456), AmountInput.fromText("1234.56", rub).toMinorOrNull(rub))
        assertEquals(Minor(123456), AmountInput.fromText("1234,56", rub).toMinorOrNull(rub))
        assertEquals(Minor(150000), AmountInput.fromText("1500", rub).toMinorOrNull(rub))
    }

    @Test
    fun `fromText ignores thousands spaces and caps the fraction`() {
        assertEquals(Minor(123456), AmountInput.fromText("1 234.567", rub).toMinorOrNull(rub))
        assertEquals(Minor(5000), AmountInput.fromText("50.00", rub).toMinorOrNull(rub))
        assertEquals(Minor(50), AmountInput.fromText("50.7", clp).toMinorOrNull(clp)) // CLP: всё после точки отброшено
    }

    @Test
    fun `fromText on blank is empty`() {
        assertNull(AmountInput.fromText("", rub).toMinorOrNull(rub))
        assertNull(AmountInput.fromText("   ", rub).toMinorOrNull(rub))
    }

    @Test
    fun `fromMinor round-trips back through toMinorOrNull`() {
        for (raw in listOf(0L, 5L, 1500L, 1205L, 999999L)) {
            assertEquals(Minor(raw), AmountInput.fromMinor(Minor(raw), rub).toMinorOrNull(rub) ?: Minor(0))
        }
        assertEquals("15", AmountInput.fromMinor(Minor(1500), rub).displayText())
        assertEquals("12.05", AmountInput.fromMinor(Minor(1205), rub).displayText())
        assertEquals("42", AmountInput.fromMinor(Minor(42), clp).displayText())
    }

    @Test
    fun `fromText does not depend on the default locale`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val us = AmountInput.fromText("1234,56", rub).toMinorOrNull(rub)
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            val ru = AmountInput.fromText("1234,56", rub).toMinorOrNull(rub)
            assertEquals(Minor(123456), us)
            assertEquals(us, ru)
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
