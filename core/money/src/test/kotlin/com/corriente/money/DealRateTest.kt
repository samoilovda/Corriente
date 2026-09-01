package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DealRateTest {

    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val usd = Currency(CurrencyCode("USD"), minorUnits = 2, displayScale = 2, symbol = "$")
    private val jpy = Currency(CurrencyCode("JPY"), minorUnits = 0, displayScale = 0, symbol = "¥")

    private fun money(major: String, c: Currency) =
        Money(Minor(BigDecimal(major).movePointRight(c.minorUnits).toLong()), c.code)

    // Приёмка этапа 2: перевод 8 695 ₽ → 100 $ показывает курс 86.95.
    @Test
    fun `rate is toAmount over fromAmount`() {
        val rub8695 = money("8695", rub)
        val usd100 = money("100", usd)
        // «1 USD = ? RUB»: from = 100 USD, to = 8695 RUB
        assertEquals(0, BigDecimal("86.95").compareTo(DealRate.rate(usd100, usd, rub8695, rub)))
        assertEquals("1 USD = 86.95 RUB", DealRate.format(usd100, usd, rub8695, rub))
        // обратное направление тоже определено
        assertEquals("1 RUB = 0.011501 USD", DealRate.format(rub8695, rub, usd100, usd))
    }

    @Test
    fun `rate is null when the source amount is zero`() {
        assertNull(DealRate.rate(Money(Minor(0), rub.code), rub, money("100", usd), usd))
        assertNull(DealRate.format(Money(Minor(0), rub.code), rub, money("100", usd), usd))
    }

    @Test
    fun `applyRate recomputes the receiving amount and rounds once`() {
        // 100 USD по курсу 86.95 -> 8695.00 RUB
        assertEquals(Minor(8_695_00), DealRate.applyRate(money("100", usd), usd, BigDecimal("86.95"), rub))
        // округление до минорных единиц приёмника ровно один раз: 1 USD * 86.955 = 86.955 -> 86.96 RUB (HALF_UP)
        assertEquals(Minor(86_96), DealRate.applyRate(money("1", usd), usd, BigDecimal("86.955"), rub))
        // приёмник без минорных единиц (JPY): 12.34 USD * 100 -> 1234
        assertEquals(Minor(1234), DealRate.applyRate(money("12.34", usd), usd, BigDecimal("100"), jpy))
    }

    @Test
    fun `applyRate and applyRateInverse round-trip within one minor unit`() {
        val from = money("8695", rub)
        val rate = DealRate.rate(from, rub, money("100", usd), usd)!!
        val to = DealRate.applyRate(from, rub, rate, usd)
        assertEquals(Minor(100_00), to)
        val back = DealRate.applyRateInverse(Money(to, usd.code), usd, rate, rub)
        assertEquals(Minor(8_695_00), back)
    }
}
