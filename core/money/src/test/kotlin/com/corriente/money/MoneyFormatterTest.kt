package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyFormatterTest {

    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val uzs = Currency(CurrencyCode("UZS"), minorUnits = 2, displayScale = 0, symbol = "сум")
    private val clp = Currency(CurrencyCode("CLP"), minorUnits = 0, displayScale = 0, symbol = "$")

    @Test
    fun `formats with thousands grouping and two decimals`() {
        val money = Money(Minor(5_509_175), rub.code)
        assertEquals("55\u00A0091.75\u00A0₽", MoneyFormatter.format(money, rub))
    }

    @Test
    fun `formats negative amounts with a leading minus`() {
        val money = Money(Minor(-125_075), rub.code)
        assertEquals("-1\u00A0250.75\u00A0₽", MoneyFormatter.format(money, rub))
    }

    @Test
    fun `formats zero`() {
        assertEquals("0.00\u00A0₽", MoneyFormatter.format(Money.zero(rub.code), rub))
    }

    @Test
    fun `formats small amounts without a leading grouping separator`() {
        assertEquals("9.99\u00A0₽", MoneyFormatter.format(Money(Minor(999), rub.code), rub))
    }

    // UZS stored with minorUnits=2 but displayed with 0 digits (docs/ARCHITECTURE.md §2.1).
    @Test
    fun `displayScale below minorUnits hides the fractional part`() {
        val money = Money(Minor(6_000_000), uzs.code) // 60 000.00 сум, хранится в тийинах
        assertEquals("60\u00A0000\u00A0сум", MoneyFormatter.format(money, uzs))
    }

    @Test
    fun `zero minor units currency has no decimal point at all`() {
        val money = Money(Minor(1_234_567), clp.code)
        assertEquals("1\u00A0234\u00A0567\u00A0$", MoneyFormatter.format(money, clp))
    }

    @Test
    fun `throws if formatted with the wrong currency`() {
        val money = Money(Minor(100), rub.code)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.format(money, uzs)
        }
    }

    // I-25: результат не должен зависеть от Locale.setDefault().
    @Test
    fun `does not depend on the default locale`() {
        val originalLocale = Locale.getDefault()
        val money = Money(Minor(5_509_175), rub.code)
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val ru = MoneyFormatter.format(money, rub)
            Locale.setDefault(Locale.US)
            val us = MoneyFormatter.format(money, rub)
            Locale.setDefault(Locale.GERMANY)
            val de = MoneyFormatter.format(money, rub)
            assertEquals(ru, us)
            assertEquals(us, de)
            assertEquals("55\u00A0091.75\u00A0₽", ru)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    // A3.5: при displayScale < minorUnits дробь усекается — «-0 сум» не выводим, знак только у ненулевой величины.
    @Test
    fun `a negative amount that displays as zero has no minus sign`() {
        val uzs = Currency(CurrencyCode("UZS"), minorUnits = 2, displayScale = 0, symbol = "сум")
        assertEquals("0\u00A0сум", MoneyFormatter.format(Money(Minor(-50), uzs.code), uzs))
        assertEquals("-1\u00A0сум", MoneyFormatter.format(Money(Minor(-100), uzs.code), uzs))
        assertEquals("0.00\u00A0₽", MoneyFormatter.format(Money(Minor(0), CurrencyCode("RUB")), Currency(CurrencyCode("RUB"), 2, 2, "₽")))
    }

    // A3.5: тысячи и отступ перед символом — неразрывный пробел (U+00A0), обычных пробелов в сумме нет.
    @Test
    fun `separators are non-breaking spaces so a long amount cannot wrap in the middle`() {
        val rub = Currency(CurrencyCode("RUB"), 2, 2, "₽")
        val text = MoneyFormatter.format(Money(Minor(123_456_789), rub.code), rub)
        assertEquals("1\u00A0234\u00A0567.89\u00A0₽", text)
        assertEquals(false, text.contains(' '))
    }

    // A3.15: число без группировки и символа.
    @Test
    fun `formatPlain has no grouping and no symbol`() {
        assertEquals("-1234.56", MoneyFormatter.formatPlain(Money(Minor(-123_456), rub.code), rub))
        assertEquals("55091.75", MoneyFormatter.formatPlain(Money(Minor(5_509_175), rub.code), rub))
        assertEquals("0", MoneyFormatter.formatPlain(Money(Minor(-50), uzs.code), uzs))
        assertEquals("1234567", MoneyFormatter.formatPlain(Money(Minor(1_234_567), clp.code), clp))
    }
}
