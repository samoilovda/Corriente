package com.corriente.money

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
    CalcOp.TIMES -> Minor(roundedDiv(Math.multiplyExact(acc.raw, operand.raw), pow10(minorUnits)))
    CalcOp.DIVIDE ->
        if (operand.raw == 0L) acc
        else Minor(roundedDiv(Math.multiplyExact(acc.raw, pow10(minorUnits)), operand.raw))
}

private fun pow10(n: Int): Long {
    var p = 1L
    repeat(n) { p = Math.multiplyExact(p, 10L) }
    return p
}

/** Целочисленное деление с округлением половины от нуля; переполнение при `+bump` роняет (I-3). */
private fun roundedDiv(numerator: Long, denominator: Long): Long {
    val q = numerator / denominator
    val r = numerator % denominator
    if (r == 0L) return q
    val absR = Math.abs(r)
    val absD = Math.abs(denominator)
    val bump = if ((numerator xor denominator) < 0) -1L else 1L
    // absR*2 >= absD, переписано без умножения, чтобы не переполнить Long на больших остатках.
    return if (absR >= absD - absR) Math.addExact(q, bump) else q
}
