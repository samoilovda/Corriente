package com.corriente.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Плоский JVM-тест на статические данные — не требует Android SDK для выполнения, но модуль
 * :core:data целиком требует AGP для конфигурации Gradle (см. README "Известное ограничение
 * окружения"), поэтому здесь не запускался.
 */
class IsoCurrenciesTest {

    @Test
    fun `no duplicate currency codes`() {
        val codes = ISO_CURRENCIES.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `all codes are three uppercase letters`() {
        assertTrue(ISO_CURRENCIES.all { it.code.matches(Regex("[A-Z]{3}")) })
    }

    @Test
    fun `all minor units are within supported range`() {
        // core.money.Currency допускает 0..4 (докстрока Currency.kt) - CLF/UYW реально имеют 4.
        assertTrue(ISO_CURRENCIES.all { it.minorUnits in 0..4 })
    }

    @Test
    fun `no precious metal, SDR, test or no-currency codes leaked in`() {
        val forbidden = setOf("XAU", "XAG", "XPD", "XPT", "XDR", "XTS", "XXX", "XSU", "XUA")
        assertTrue(ISO_CURRENCIES.none { it.code in forbidden })
    }

    @Test
    fun `target currencies from the requirements are present with correct minor units`() {
        val byCode = ISO_CURRENCIES.associateBy { it.code }
        assertEquals(2, byCode.getValue("RUB").minorUnits)
        assertEquals(2, byCode.getValue("USD").minorUnits)
        assertEquals(2, byCode.getValue("KZT").minorUnits)
        assertEquals(2, byCode.getValue("UZS").minorUnits)
        assertEquals(2, byCode.getValue("KGS").minorUnits)
        assertEquals(0, byCode.getValue("CLP").minorUnits) // ISO-4217: чилийское песо без копеек
        assertEquals(2, byCode.getValue("NZD").minorUnits)
    }

    @Test
    fun `has more than 150 active currencies - full ISO-4217, not a curated shortlist`() {
        assertTrue(ISO_CURRENCIES.size > 150)
    }
}
