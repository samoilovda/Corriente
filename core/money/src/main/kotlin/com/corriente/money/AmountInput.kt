package com.corriente.money

/**
 * Состояние калькуляторной клавиатуры ввода суммы (T1.5). Собирается по одной цифре за раз,
 * поэтому в отличие от разбора свободного текста здесь нет разделителя тысяч и нет
 * неоднозначности между "," и "." — инвариант I-25 соблюдается конструктивно, без парсинга.
 *
 * Сознательное сужение задачи из BUILD_PLAN.md T0.3 (см. итоговое сообщение): вместо
 * "парсера свободного текста" вида "1,234.56"/"1 234,56" ввод суммы в приложении — это
 * калькуляторная клавиатура, где строка вида "1,234.56" в принципе не может возникнуть.
 * Разбор свободного текста с разделителями оставлен только для формата Monefy
 * ([MonefyAmountParser]), где формат зафиксирован спецификацией, а не гадается по локали.
 */
data class AmountInput(
    private val integerDigits: String = "",
    private val fractionDigits: String = "",
    private val hasDecimalPoint: Boolean = false,
) {
    fun appendDigit(digit: Char, currency: Currency): AmountInput {
        require(digit in '0'..'9') { "Not a digit: '$digit'" }
        if (!hasDecimalPoint) {
            if (integerDigits == "0") return copy(integerDigits = digit.toString())
            return copy(integerDigits = integerDigits + digit)
        }
        if (fractionDigits.length >= currency.minorUnits) return this
        return copy(fractionDigits = fractionDigits + digit)
    }

    /** Нажатие "." или "," на клавиатуре — обе клавиши означают десятичный разделитель. */
    fun appendDecimalPoint(currency: Currency): AmountInput {
        if (currency.minorUnits == 0) return this
        if (hasDecimalPoint) return this
        return copy(hasDecimalPoint = true)
    }

    fun backspace(): AmountInput = when {
        fractionDigits.isNotEmpty() -> copy(fractionDigits = fractionDigits.dropLast(1))
        hasDecimalPoint -> copy(hasDecimalPoint = false)
        integerDigits.isNotEmpty() -> copy(integerDigits = integerDigits.dropLast(1))
        else -> this
    }

    val isEmpty: Boolean get() = integerDigits.isEmpty() && fractionDigits.isEmpty() && !hasDecimalPoint

    /** Текст для отображения на экране ввода, ещё до подтверждения суммы. */
    fun displayText(): String = buildString {
        append(integerDigits.ifEmpty { "0" })
        if (hasDecimalPoint) {
            append('.')
            append(fractionDigits)
        }
    }

    /** @return сумма в минорных единицах [currency], или null, если ничего не введено. */
    fun toMinorOrNull(currency: Currency): Minor? {
        if (isEmpty) return null
        val paddedFraction = fractionDigits.padEnd(currency.minorUnits, '0')
        val value = (integerDigits.ifEmpty { "0" } + paddedFraction).toLong()
        return Minor(value)
    }

    companion object {
        fun empty(): AmountInput = AmountInput()
    }
}
