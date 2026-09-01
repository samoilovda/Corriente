package com.corriente.money

/**
 * T5.5: минимальный калькулятор в поле суммы — сложение и вычитание сумм одной валюты.
 * Умножение/деление денег на деньги не имеет смысла, поэтому их нет. Одна отложенная
 * операция за раз (как на простом калькуляторе); переполнение роняет (I-3).
 */
enum class CalcOp(val symbol: Char) { PLUS('+'), MINUS('−') }

/** Применяет [op] к накопителю [acc] и операнду [operand]. Обе величины — минорные единицы одной валюты. */
fun applyCalc(acc: Minor, op: CalcOp, operand: Minor): Minor = when (op) {
    CalcOp.PLUS -> Minor(Math.addExact(acc.raw, operand.raw))
    CalcOp.MINUS -> Minor(Math.subtractExact(acc.raw, operand.raw))
}
