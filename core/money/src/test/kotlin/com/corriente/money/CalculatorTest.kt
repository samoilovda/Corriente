package com.corriente.money

import org.junit.Assert.assertEquals
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
}
