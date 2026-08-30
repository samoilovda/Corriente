package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты на инварианты I-1..I-3 (docs/INVARIANTS.md). */
class MoneyTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    // I-1: Money собирается только из Long минорных единиц, других конструкторов нет.
    @Test
    fun `money is backed by Long minor units, not floating point`() {
        val m = Money(Minor(12345), rub)
        assertEquals(12345L, m.amount.raw)
    }

    @Test
    fun `plus adds within same currency`() {
        val a = Money(Minor(1000), rub)
        val b = Money(Minor(250), rub)
        assertEquals(Money(Minor(1250), rub), a + b)
    }

    @Test
    fun `minus subtracts within same currency`() {
        val a = Money(Minor(1000), rub)
        val b = Money(Minor(250), rub)
        assertEquals(Money(Minor(750), rub), a - b)
    }

    @Test
    fun `unaryMinus negates`() {
        assertEquals(Money(Minor(-500), rub), -Money(Minor(500), rub))
    }

    @Test
    fun `times multiplies by integer factor`() {
        assertEquals(Money(Minor(300), rub), Money(Minor(100), rub) * 3)
    }

    // I-2: разные валюты не складываются молча.
    @Test
    fun `plus with mismatched currencies throws`() {
        val a = Money(Minor(100), rub)
        val b = Money(Minor(100), usd)
        assertThrows(CurrencyMismatchException::class.java) { a + b }
    }

    @Test
    fun `minus with mismatched currencies throws`() {
        assertThrows(CurrencyMismatchException::class.java) {
            Money(Minor(100), rub) - Money(Minor(100), usd)
        }
    }

    @Test
    fun `compareTo with mismatched currencies throws`() {
        assertThrows(CurrencyMismatchException::class.java) {
            Money(Minor(100), rub) < Money(Minor(100), usd)
        }
    }

    @Test
    fun `compareTo orders within same currency`() {
        assertTrue(Money(Minor(100), rub) < Money(Minor(200), rub))
        assertTrue(Money(Minor(200), rub) > Money(Minor(100), rub))
        assertEquals(0, Money(Minor(100), rub).compareTo(Money(Minor(100), rub)))
    }

    // I-3: переполнение падает, а не даёт неверное число.
    @Test
    fun `plus overflow throws instead of wrapping`() {
        val a = Money(Minor(Long.MAX_VALUE), rub)
        val b = Money(Minor(1), rub)
        assertThrows(ArithmeticException::class.java) { a + b }
    }

    @Test
    fun `minus overflow throws instead of wrapping`() {
        val a = Money(Minor(Long.MIN_VALUE), rub)
        val b = Money(Minor(1), rub)
        assertThrows(ArithmeticException::class.java) { a - b }
    }

    @Test
    fun `unaryMinus overflow throws instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) { -Money(Minor(Long.MIN_VALUE), rub) }
    }

    @Test
    fun `times overflow throws instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) {
            Money(Minor(Long.MAX_VALUE / 2), rub) * 3
        }
    }

    @Test
    fun `sign predicates`() {
        assertTrue(Money(Minor(-1), rub).isNegative)
        assertFalse(Money(Minor(-1), rub).isPositive)
        assertTrue(Money(Minor(1), rub).isPositive)
        assertTrue(Money(Minor(0), rub).isZero)
    }

    @Test
    fun `absolute flips sign only when negative`() {
        assertEquals(Money(Minor(5), rub), Money(Minor(-5), rub).absolute())
        assertEquals(Money(Minor(5), rub), Money(Minor(5), rub).absolute())
    }

    @Test
    fun `zero attaches currency to a zero amount`() {
        assertEquals(Money(Minor(0), rub), Money.zero(rub))
    }

    @Test
    fun `sumMoney reduces a list of same-currency amounts`() {
        val list = listOf(Money(Minor(100), rub), Money(Minor(200), rub), Money(Minor(50), rub))
        assertEquals(Money(Minor(350), rub), list.sumMoney())
    }

    @Test
    fun `sumMoney throws on empty list - no currency to attach zero to`() {
        assertThrows(IllegalArgumentException::class.java) { emptyList<Money>().sumMoney() }
    }

    @Test
    fun `sumMoney throws on mixed currencies rather than silently converting`() {
        val list = listOf(Money(Minor(100), rub), Money(Minor(100), usd))
        assertThrows(CurrencyMismatchException::class.java) { list.sumMoney() }
    }

    @Test
    fun `currency code rejects non ISO-4217 shapes`() {
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("rub") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("RU") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("RUBL") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("R1B") }
    }
}
