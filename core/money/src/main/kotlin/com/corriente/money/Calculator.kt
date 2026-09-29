package com.corriente.money

import java.math.BigInteger

/**
 * T5.5 (+ доработка): калькулятор в поле суммы.
 *
 * Сложение и вычитание — арифметика над суммами одной валюты (I-2 уже обеспечен тем, что
 * калькулятор живёт в пределах одного выбранного счёта). Умножение и деление трактуются как
 * «сумма ⊗ число»: второй операнд — множитель/делитель, а не сумма (10 ₽ × 3 = 30 ₽,
 * 100 ₽ ÷ 4 = 25 ₽). Поэтому им нужен масштаб минорной единицы валюты [minorUnits], иначе
 * «3» и «3.00» дали бы разный результат.
 *
 * Одна отложенная операция за раз (как на простом калькуляторе); переполнение роняет (I-3);
 * деление на ноль возвращает накопитель без изменений (нечего показать, ронять кадр незачем).
 */
enum class CalcOp(val symbol: Char) { PLUS('+'), MINUS('−'), TIMES('×'), DIVIDE('÷') }

/**
 * Применяет [op] к накопителю [acc] и операнду [operand]. Для «+»/«−» обе величины — минорные
 * единицы одной валюты. Для «×»/«÷» [operand] — множитель (его «майорное» значение), а
 * [minorUnits] — число десятичных знаков минорной единицы валюты (2 для ₽/$, 0 для ¥/₩);
 * результат округляется до минорной единицы (половина — от нуля).
 */
fun applyCalc(acc: Minor, op: CalcOp, operand: Minor, minorUnits: Int = 0): Minor = when (op) {
    CalcOp.PLUS -> Minor(Math.addExact(acc.raw, operand.raw))
    CalcOp.MINUS -> Minor(Math.subtractExact(acc.raw, operand.raw))
    // Промежуточное произведение считаем в BigInteger: `acc × operand` может не влезть в Long,
    // хотя после деления на 10^minorUnits результат влезает. Переполнение проверяется на итоге.
    CalcOp.TIMES -> Minor(
        roundedDiv(BigInteger.valueOf(acc.raw).multiply(BigInteger.valueOf(operand.raw)), pow10(minorUnits)),
    )
    CalcOp.DIVIDE ->
        if (operand.raw == 0L) acc
        else Minor(roundedDiv(BigInteger.valueOf(acc.raw).multiply(pow10(minorUnits)), BigInteger.valueOf(operand.raw)))
}

/**
 * Максимум значащих цифр суммы, которую принимает [AmountInput] (см. `MAX_TOTAL_DIGITS`) — результат
 * калькулятора, не помещающийся в это число цифр, экран не сможет показать и отредактировать заново.
 */
const val MAX_CALC_ABS: Long = 999_999_999_999_999_999L

/**
 * Как [applyCalc], но переполнение (в `Long` или сверх [MAX_CALC_ABS]) даёт `null`, а не исключение:
 * вызывающий код на главном потоке не должен падать от нажатия клавиши (A0.2).
 */
fun applyCalcOrNull(acc: Minor, op: CalcOp, operand: Minor, minorUnits: Int = 0): Minor? {
    val result = try {
        applyCalc(acc, op, operand, minorUnits)
    } catch (_: ArithmeticException) {
        return null
    }
    return result.takeIf { Math.abs(it.raw) <= MAX_CALC_ABS && it.raw != Long.MIN_VALUE }
}

private fun pow10(n: Int): BigInteger = BigInteger.TEN.pow(n)

/** Целочисленное деление с округлением половины от нуля; результат вне `Long` роняет (I-3). */
private fun roundedDiv(numerator: BigInteger, denominator: BigInteger): Long {
    val (q, r) = numerator.divideAndRemainder(denominator)
    val rounded = if (r.signum() != 0 && r.abs().shiftLeft(1) >= denominator.abs()) {
        q.add(BigInteger.valueOf(if (numerator.signum() * denominator.signum() < 0) -1L else 1L))
    } else {
        q
    }
    if (rounded.bitLength() > 63) throw ArithmeticException("calculator result overflows Long")
    return rounded.toLong()
}
