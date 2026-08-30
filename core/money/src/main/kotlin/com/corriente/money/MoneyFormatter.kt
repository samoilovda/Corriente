package com.corriente.money

/**
 * Форматирование [Money] для показа пользователю. Инвариант I-25: никаких `NumberFormat`,
 * `DecimalFormat` или иных locale-зависимых форматтеров в денежном пути — результат не должен
 * зависеть от `Locale.setDefault()`. Группировка тысяч — пробелом, дробная часть — точкой;
 * это фиксированный стиль приложения, а не локаль устройства.
 */
object MoneyFormatter {

    /**
     * Форматирует по [Currency.displayScale] (не по `minorUnits` — ARCHITECTURE.md §2.1,
     * напр. UZS хранится с копейками, но показывается без них).
     */
    fun format(money: Money, currency: Currency): String {
        require(money.currency == currency.code) {
            "Money currency ${money.currency} does not match Currency ${currency.code}"
        }
        val negative = money.isNegative
        val raw = money.amount.raw.let { if (it == Long.MIN_VALUE) it else Math.abs(it) }
        val rawUnsigned = if (money.amount.raw == Long.MIN_VALUE) {
            // Math.abs(Long.MIN_VALUE) переполняется; обрабатываем как строку.
            money.amount.raw.toString().removePrefix("-")
        } else {
            raw.toString()
        }

        val minorUnits = currency.minorUnits
        val displayScale = currency.displayScale
        val padded = rawUnsigned.padStart(minorUnits + 1, '0')
        val integerDigits = padded.dropLast(minorUnits).ifEmpty { "0" }
        val fractionDigits = padded.takeLast(minorUnits)

        val groupedInteger = groupThousands(integerDigits)

        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(groupedInteger)
        if (displayScale > 0) {
            sb.append('.')
            sb.append(roundFraction(fractionDigits, displayScale))
        }
        sb.append(' ').append(currency.symbol)
        return sb.toString()
    }

    private fun groupThousands(digits: String): String {
        val sb = StringBuilder()
        for ((index, char) in digits.reversed().withIndex()) {
            if (index != 0 && index % 3 == 0) sb.append(' ')
            sb.append(char)
        }
        return sb.reverse().toString()
    }

    /** Обрезает дробную часть до displayScale знаков (без округления вверх — усечение). */
    private fun roundFraction(fractionDigits: String, displayScale: Int): String =
        fractionDigits.take(displayScale).padEnd(displayScale, '0')
}
