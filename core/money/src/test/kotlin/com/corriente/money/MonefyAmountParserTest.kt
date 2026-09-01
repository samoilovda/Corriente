package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

/**
 * Тесты на I-25 и на формат docs/MONEFY_IMPORT.md §2, включая ровно те примеры,
 * которые встретились в реальном экспорте (docs/MONEFY_IMPORT.md §4, testdata/monefy_sample.csv).
 */
class MonefyAmountParserTest {

    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val usd = Currency(CurrencyCode("USD"), minorUnits = 2, displayScale = 2, symbol = "$")

    @Test
    fun `comma is a thousands separator, not decimal`() {
        // "49,120" из реального экспорта - это 49 120, а не 49.12 (докстрока MonefyAmountParser).
        assertEquals(Minor(4_912_000), MonefyAmountParser.parse("49,120", rub))
    }

    @Test
    fun `dot is the decimal separator`() {
        assertEquals(Minor(-2000), MonefyAmountParser.parse("-20.00", rub))
    }

    @Test
    fun `combines thousands comma and decimal dot`() {
        // "-1,250.75" из monefy_sample.expected.md -> -1250.75 RUB -> -125075 копеек.
        assertEquals(Minor(-125_075), MonefyAmountParser.parse("-1,250.75", rub))
    }

    @Test
    fun `plain integer without separators`() {
        assertEquals(Minor(-2000), MonefyAmountParser.parse("-20", rub))
        assertEquals(Minor(5_000_000), MonefyAmountParser.parse("50,000", rub))
    }

    @Test
    fun `three nonzero fractional digits from a real anomalous row are rejected, not rounded`() {
        // "100.001" USD из docs/MONEFY_IMPORT.md §4 (та самая строка с аномальной валютой).
        // USD хранится с двумя минорными единицами; третий ненулевой знак нельзя тихо
        // округлить (I-4) - импортёр обязан отдать эту строку в NEEDS_REVIEW, а не угадать.
        assertThrows(IllegalArgumentException::class.java) {
            MonefyAmountParser.parse("100.001", usd)
        }
    }

    @Test
    fun `does not depend on the default locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val ru = MonefyAmountParser.parse("49,120", rub)
            Locale.setDefault(Locale.US)
            val us = MonefyAmountParser.parse("49,120", rub)
            assertEquals(ru, us)
            assertEquals(Minor(4_912_000), ru)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `rejects malformed amounts instead of guessing`() {
        assertThrows(IllegalArgumentException::class.java) { MonefyAmountParser.parse("", rub) }
        assertThrows(IllegalArgumentException::class.java) { MonefyAmountParser.parse("abc", rub) }
        assertThrows(IllegalArgumentException::class.java) { MonefyAmountParser.parse("1.2.3", rub) }
    }

    @Test
    fun `refuses to silently truncate fractional digits beyond currency scale`() {
        // Явно избыточная точность - лучше упасть, чем тихо округлить (I-4 дух).
        assertThrows(IllegalArgumentException::class.java) {
            MonefyAmountParser.parse("1.2345", rub) // RUB has only 2 minor units
        }
    }

    @Test
    fun `trailing zero extra digits beyond scale are tolerated`() {
        assertEquals(Minor(150), MonefyAmountParser.parse("1.500", rub))
    }

    // --- parseLenient: для импорта, где строку с избыточной точностью нельзя потерять ---

    @Test
    fun `parseLenient rounds excess fractional digits HALF_UP and flags it`() {
        val a = MonefyAmountParser.parseLenient("100.001", usd) // третий знак .001 -> 100.00
        assertEquals(Minor(100_00), a.minor)
        assertEquals(true, a.roundedFromExcessPrecision)

        val b = MonefyAmountParser.parseLenient("799.976", usd) // .976 -> 799.98
        assertEquals(Minor(799_98), b.minor)
        assertEquals(true, b.roundedFromExcessPrecision)
    }

    @Test
    fun `parseLenient does not flag amounts that fit the currency scale`() {
        assertEquals(
            MonefyAmountParser.Lenient(Minor(-125_075), false),
            MonefyAmountParser.parseLenient("-1,250.75", rub),
        )
        assertEquals(false, MonefyAmountParser.parseLenient("1.500", rub).roundedFromExcessPrecision)
    }
}
