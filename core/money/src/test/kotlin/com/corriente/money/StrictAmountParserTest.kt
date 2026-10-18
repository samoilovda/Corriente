package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Test

/** U01: строгий разбор порога суммы с точностью валюты. */
class StrictAmountParserTest {

    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val jpy = Currency(CurrencyCode("JPY"), minorUnits = 0, displayScale = 0, symbol = "¥")
    private val kwd = Currency(CurrencyCode("KWD"), minorUnits = 3, displayScale = 3, symbol = "KD")
    private val clf = Currency(CurrencyCode("CLF"), minorUnits = 4, displayScale = 4, symbol = "UF")

    private fun parse(text: String, currency: Currency) = StrictAmountParser.parse(text, currency)
    private fun value(raw: Long) = AmountParse.Value(Minor(raw))
    private fun invalid(error: AmountParseError) = AmountParse.Invalid(error)

    @Test
    fun `acceptance values from the audit`() {
        assertEquals(value(12_550), parse("125.50", rub))
        assertEquals(value(125), parse("125", jpy))
        assertEquals(value(1_234), parse("1.234", kwd))
        assertEquals(value(12_345), parse("1.2345", clf))
    }

    @Test
    fun `comma is the same decimal separator as dot`() {
        assertEquals(value(12_550), parse("125,50", rub))
        assertEquals(value(12_550), parse("125,5", rub))
        assertEquals(value(50), parse(",5", rub))
        assertEquals(value(12_500), parse("125.", rub))
    }

    @Test
    fun `blank text is an explicit absence of value`() {
        assertEquals(AmountParse.Empty, parse("", rub))
        assertEquals(AmountParse.Empty, parse("   ", rub))
        assertEquals(value(100), parse(" 1 ", rub))
    }

    @Test
    fun `excess precision is an error, not a silent truncation`() {
        assertEquals(invalid(AmountParseError.TOO_MANY_DECIMALS), parse("125.505", rub))
        assertEquals(invalid(AmountParseError.TOO_MANY_DECIMALS), parse("125.5", jpy))
        assertEquals(invalid(AmountParseError.TOO_MANY_DECIMALS), parse("1.23456", clf))
        assertEquals(value(125), parse("125.", jpy))
    }

    @Test
    fun `garbage and grouping are rejected`() {
        assertEquals(invalid(AmountParseError.NOT_A_NUMBER), parse("12a", rub))
        assertEquals(invalid(AmountParseError.NOT_A_NUMBER), parse("1 000", rub))
        assertEquals(invalid(AmountParseError.NOT_A_NUMBER), parse("1.000,50", rub))
        assertEquals(invalid(AmountParseError.NOT_A_NUMBER), parse(".", rub))
        assertEquals(invalid(AmountParseError.NOT_A_NUMBER), parse("+5", rub))
        assertEquals(invalid(AmountParseError.NOT_A_NUMBER), parse("1e5", rub))
    }

    @Test
    fun `negative is an error`() {
        assertEquals(invalid(AmountParseError.NEGATIVE), parse("-5", rub))
        assertEquals(invalid(AmountParseError.NEGATIVE), parse("−5", rub))
    }

    @Test
    fun `long upper bound is exact, one more overflows`() {
        assertEquals(value(Long.MAX_VALUE), parse(Long.MAX_VALUE.toString(), jpy))
        assertEquals(invalid(AmountParseError.OVERFLOW), parse("9223372036854775808", jpy))
        assertEquals(value(Long.MAX_VALUE), parse("92233720368547758.07", rub))
        assertEquals(invalid(AmountParseError.OVERFLOW), parse("92233720368547758.08", rub))
        assertEquals(invalid(AmountParseError.OVERFLOW), parse("99999999999999999999999", rub))
    }

    @Test
    fun `leading zeros do not matter`() {
        assertEquals(value(5), parse("0000.05", rub))
    }
}
