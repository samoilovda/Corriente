package com.corriente.money

/** Почему свободный текст не является суммой (U01). Текст ошибки — забота UI. */
enum class AmountParseError {
    /** Посторонние символы, два разделителя, ни одной цифры. */
    NOT_A_NUMBER,

    /** Знак минус: порог/сумма — модуль, знак операции живёт в её типе (I-1). */
    NEGATIVE,

    /** Дробных знаков больше, чем `minorUnits` валюты — молча не обрезаем. */
    TOO_MANY_DECIMALS,

    /** Не помещается в Long минорных единиц (I-3: ошибка, а не wraparound). */
    OVERFLOW,
}

sealed interface AmountParse {
    /** Пустое поле — явное отсутствие значения (напр. «нет нижней границы»), не ошибка. */
    data object Empty : AmountParse

    data class Value(val minor: Minor) : AmountParse

    data class Invalid(val error: AmountParseError) : AmountParse
}

/**
 * Строгий разбор суммы, набранной в обычном текстовом поле (U01: пороги фильтра). В отличие от
 * [AmountInput.fromText], который пропускает посторонние символы и молча режет лишние цифры (он
 * собирает ввод клавиатуры), здесь любой непредусмотренный ввод — [AmountParse.Invalid].
 *
 * Формат тот же, что у клавиатуры ввода (I-25, без локали): цифры и не более одного десятичного
 * разделителя, `.` или `,`. Разделителей тысяч нет. Пробелы по краям игнорируются.
 * Точность — [Currency.minorUnits]: «125.50» RUB → 12550, «125» JPY → 125, «1.234» KWD → 1234.
 */
object StrictAmountParser {

    fun parse(text: String, currency: Currency): AmountParse {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AmountParse.Empty
        if (trimmed.startsWith('-') || trimmed.startsWith('−')) return AmountParse.Invalid(AmountParseError.NEGATIVE)

        val separatorAt = trimmed.indexOfFirst { it == '.' || it == ',' }
        val integerPart = if (separatorAt < 0) trimmed else trimmed.substring(0, separatorAt)
        val fractionPart = if (separatorAt < 0) "" else trimmed.substring(separatorAt + 1)
        val allDigits = integerPart.all { it in '0'..'9' } && fractionPart.all { it in '0'..'9' }
        if (!allDigits || integerPart.isEmpty() && fractionPart.isEmpty()) {
            return AmountParse.Invalid(AmountParseError.NOT_A_NUMBER)
        }
        if (fractionPart.length > currency.minorUnits) return AmountParse.Invalid(AmountParseError.TOO_MANY_DECIMALS)

        return try {
            var value = 0L
            for (digit in integerPart + fractionPart.padEnd(currency.minorUnits, '0')) {
                value = Math.addExact(Math.multiplyExact(value, 10L), (digit - '0').toLong())
            }
            AmountParse.Value(Minor(value))
        } catch (_: ArithmeticException) {
            AmountParse.Invalid(AmountParseError.OVERFLOW)
        }
    }
}
