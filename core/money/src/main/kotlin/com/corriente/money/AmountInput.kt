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

        /**
         * Сборка из уже набранной строки (напр. поле "начальный остаток", T1.3) — прогоняет
         * символы через тот же [appendDigit]/[appendDecimalPoint], что и клавиатура, поэтому
         * так же не зависит от локали (I-25). И `.`, и `,` — десятичный разделитель; всё
         * остальное (пробелы-разделители тысяч и пр.) игнорируется.
         */
        fun fromText(text: String, currency: Currency): AmountInput {
            var input = empty()
            for (char in text) {
                when {
                    char in '0'..'9' -> input = input.appendDigit(char, currency)
                    char == '.' || char == ',' -> {
                        // У валюты без минорных единиц дробной части нет — всё после разделителя отбрасываем.
                        if (currency.minorUnits == 0) return input
                        input = input.appendDecimalPoint(currency)
                    }
                }
            }
            return input
        }
    }
}
