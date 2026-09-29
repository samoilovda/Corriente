package com.corriente.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** T5.5: калькулятор суммы — сложение/вычитание минорных единиц, переполнение роняет (I-3). */
class CalculatorTest {

    @Test
    fun `plus and minus operate on minor units`() {
        assertEquals(Minor(1_570), applyCalc(Minor(1_250), CalcOp.PLUS, Minor(320)))
        assertEquals(Minor(930), applyCalc(Minor(1_250), CalcOp.MINUS, Minor(320)))
        assertEquals(Minor(-70), applyCalc(Minor(250), CalcOp.MINUS, Minor(320)))
    }

    @Test
    fun `overflow throws instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) {
            applyCalc(Minor(Long.MAX_VALUE), CalcOp.PLUS, Minor(1))
        }
    }

    @Test
    fun `times and divide treat the operand as a multiplier scaled by minorUnits`() {
        // 10.00 × 5  → 50.00   (minorUnits = 2: 1000 * 500 / 100)
        assertEquals(Minor(5_000), applyCalc(Minor(1_000), CalcOp.TIMES, Minor(500), minorUnits = 2))
        // 100.00 ÷ 4 → 25.00
        assertEquals(Minor(2_500), applyCalc(Minor(10_000), CalcOp.DIVIDE, Minor(400), minorUnits = 2))
        // Валюта без минорной части (¥): 300 × 3 = 900.
        assertEquals(Minor(900), applyCalc(Minor(300), CalcOp.TIMES, Minor(3), minorUnits = 0))
    }

    @Test
    fun `divide rounds half away from zero`() {
        // 10.00 ÷ 3 = 3.333… → 3.33
        assertEquals(Minor(333), applyCalc(Minor(1_000), CalcOp.DIVIDE, Minor(300), minorUnits = 2))
        // 0.10 ÷ 3 = 0.0333 → 0.03
        assertEquals(Minor(3), applyCalc(Minor(10), CalcOp.DIVIDE, Minor(300), minorUnits = 2))
        // 1.00 × 2.5 = 2.50, ровно
        assertEquals(Minor(250), applyCalc(Minor(100), CalcOp.TIMES, Minor(250), minorUnits = 2))
        // 5 ÷ 2 = 2.5 → 3 (у валюты без дробной части)
        assertEquals(Minor(3), applyCalc(Minor(5), CalcOp.DIVIDE, Minor(2), minorUnits = 0))
    }

    @Test
    fun `divide by zero keeps the accumulator`() {
        assertEquals(Minor(1_000), applyCalc(Minor(1_000), CalcOp.DIVIDE, Minor(0), minorUnits = 2))
    }

    @Test
    fun `times overflow throws instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) {
            applyCalc(Minor(Long.MAX_VALUE), CalcOp.TIMES, Minor(1_000), minorUnits = 2)
        }
    }

    @Test
    fun `times does not overflow on the intermediate product`() {
        // 10^17 × 3.00: произведение 3·10^19 не влезает в Long, результат 3·10^17 — влезает.
        assertEquals(
            Minor(300_000_000_000_000_000L),
            applyCalc(Minor(100_000_000_000_000_000L), CalcOp.TIMES, Minor(300), minorUnits = 2),
        )
    }

    @Test
    fun `applyCalcOrNull returns null on overflow and above the input limit`() {
        assertNull(applyCalcOrNull(Minor(Long.MAX_VALUE), CalcOp.PLUS, Minor(1)))
        assertNull(applyCalcOrNull(Minor(MAX_CALC_ABS), CalcOp.PLUS, Minor(1)))
        assertNull(applyCalcOrNull(Minor(MAX_CALC_ABS), CalcOp.DIVIDE, Minor(1), minorUnits = 2))
        assertEquals(Minor(6), applyCalcOrNull(Minor(2), CalcOp.TIMES, Minor(3), minorUnits = 0))
    }
}
