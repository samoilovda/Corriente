package com.corriente.money

/**
 * Форматирование [Money] для показа пользователю. Инвариант I-25: никаких `NumberFormat`,
 * `DecimalFormat` или иных locale-зависимых форматтеров в денежном пути — результат не должен
 * зависеть от `Locale.setDefault()`. Группировка тысяч и отступ перед символом валюты — неразрывный пробел
 * [NBSP] (A3.5: обычный пробел позволял переносу строки разорвать сумму посередине), дробная часть — точка;
 * это фиксированный стиль приложения, а не локаль устройства.
 */
object MoneyFormatter {

    /** U+00A0: тысячи и отступ перед символом валюты не переносятся на новую строку. */
    const val NBSP: Char = '\u00A0'

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
        val shownFraction = if (displayScale > 0) roundFraction(fractionDigits, displayScale) else ""

        // A3.5: знак — только если показанная величина ненулевая: при displayScale < minorUnits «-50» тийинов
        // усекается до «0» и раньше выводилось как «-0 сум».
        val shownIsZero = integerDigits.all { it == '0' } && shownFraction.all { it == '0' }

        val sb = StringBuilder()
        if (negative && !shownIsZero) sb.append('-')
        sb.append(groupedInteger)
        if (displayScale > 0) {
            sb.append('.')
            sb.append(shownFraction)
        }
        sb.append(NBSP).append(currency.symbol)
        return sb.toString()
    }

    /**
     * A3.15: число без группировки и символа — «-1234.56» (для CSV: в таблице такую ячейку можно складывать).
     * Знаков после запятой — [Currency.displayScale], как и у [format]; знак — по тому же правилу («-0» не бывает).
     */
    fun formatPlain(money: Money, currency: Currency): String {
        require(money.currency == currency.code) {
            "Money currency ${money.currency} does not match Currency ${currency.code}"
        }
        return format(money, currency).removeSuffix("${NBSP}${currency.symbol}").replace(NBSP.toString(), "")
    }

    private fun groupThousands(digits: String): String {
        val sb = StringBuilder()
        for ((index, char) in digits.reversed().withIndex()) {
            if (index != 0 && index % 3 == 0) sb.append(NBSP)
            sb.append(char)
        }
        return sb.reverse().toString()
    }

    /** Обрезает дробную часть до displayScale знаков (без округления вверх — усечение). */
    private fun roundFraction(fractionDigits: String, displayScale: Int): String =
        fractionDigits.take(displayScale).padEnd(displayScale, '0')
}
