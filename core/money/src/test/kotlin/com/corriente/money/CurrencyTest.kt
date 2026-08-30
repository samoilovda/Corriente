package com.corriente.money

import org.junit.Assert.assertThrows
import org.junit.Test

class CurrencyTest {

    @Test
    fun `accepts a standard currency definition`() {
        Currency(CurrencyCode("USD"), minorUnits = 2, displayScale = 2, symbol = "$")
    }

    // CLP has no minor unit by ISO-4217; must be representable (docs/ARCHITECTURE.md §2.1 ADR-002).
    @Test
    fun `accepts a zero-minor-unit currency like CLP`() {
        Currency(CurrencyCode("CLP"), minorUnits = 0, displayScale = 0, symbol = "$")
    }

    // UZS: stored with minorUnits=2 (tiyin) but displayed with 0 digits (docs/ARCHITECTURE.md §2.1).
    @Test
    fun `allows displayScale below minorUnits for cosmetic rounding`() {
        Currency(CurrencyCode("UZS"), minorUnits = 2, displayScale = 0, symbol = "сум")
    }

    @Test
    fun `rejects displayScale above minorUnits`() {
        assertThrows(IllegalArgumentException::class.java) {
            Currency(CurrencyCode("USD"), minorUnits = 2, displayScale = 3, symbol = "$")
        }
    }

    @Test
    fun `rejects unsupported minorUnits`() {
        assertThrows(IllegalArgumentException::class.java) {
            Currency(CurrencyCode("XXX"), minorUnits = 5, displayScale = 0, symbol = "?")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Currency(CurrencyCode("XXX"), minorUnits = -1, displayScale = 0, symbol = "?")
        }
    }
}
