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
        assertEquals("55 091.75 ₽", MoneyFormatter.format(money, rub))
    }

    @Test
    fun `formats negative amounts with a leading minus`() {
        val money = Money(Minor(-125_075), rub.code)
        assertEquals("-1 250.75 ₽", MoneyFormatter.format(money, rub))
    }

    @Test
    fun `formats zero`() {
        assertEquals("0.00 ₽", MoneyFormatter.format(Money.zero(rub.code), rub))
    }

    @Test
    fun `formats small amounts without a leading grouping separator`() {
        assertEquals("9.99 ₽", MoneyFormatter.format(Money(Minor(999), rub.code), rub))
    }

    // UZS stored with minorUnits=2 but displayed with 0 digits (docs/ARCHITECTURE.md §2.1).
    @Test
    fun `displayScale below minorUnits hides the fractional part`() {
        val money = Money(Minor(6_000_000), uzs.code) // 60 000.00 сум, хранится в тийинах
        assertEquals("60 000 сум", MoneyFormatter.format(money, uzs))
    }

    @Test
    fun `zero minor units currency has no decimal point at all`() {
        val money = Money(Minor(1_234_567), clp.code)
        assertEquals("1 234 567 $", MoneyFormatter.format(money, clp))
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
            assertEquals("55 091.75 ₽", ru)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
