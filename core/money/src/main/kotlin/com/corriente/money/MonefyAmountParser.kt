package com.corriente.money

/**
 * Разбор чисел из CSV-экспорта Monefy: запятая — разделитель тысяч, точка — десятичный
 * разделитель (docs/MONEFY_IMPORT.md §2). Правило фиксировано форматом файла, а не локалью
 * устройства — инвариант I-25 запрещает здесь любой locale-зависимый парсер
 * (`NumberFormat`, `String.toDouble()` под русской локалью прочитает "49,120" как 49.12).
 *
 * Формат подтверждён на реальном экспорте: "49,120" = 49120, "-1,250.75" = -1250.75,
 * "100.001" = 100.001 (до трёх знаков дробной части).
 */
object MonefyAmountParser {

    /**
     * @param raw строка вида "49,120", "-1,250.75", "100.001"
     * @param currency валюта, к которой приводится результат (даёт minorUnits для округления)
     * @return сумма в минорных единицах [currency]
     */
    fun parse(raw: String, currency: Currency): Minor {
        val cleaned = raw.trim().replace(",", "")
        require(cleaned.isNotEmpty()) { "Empty amount" }
        require(DECIMAL_PATTERN.matches(cleaned)) { "Not a valid Monefy amount: '$raw'" }

        val negative = cleaned.startsWith("-")
        val unsigned = cleaned.removePrefix("-")
        val (integerPart, fractionPart) = unsigned.split(".", limit = 2)
            .let { it[0] to it.getOrElse(1) { "" } }

        val scale = currency.minorUnits
        val fractionDigits = fractionPart.padEnd(scale, '0')
        require(fractionPart.length <= scale || fractionPart.drop(scale).all { it == '0' }) {
            "Amount '$raw' has more fractional digits than ${currency.code} supports " +
                "(minorUnits=$scale); refusing to silently round"
        }

        val minorString = integerPart + fractionDigits.take(scale)
        val minorValue = minorString.toLong()
        return Minor(if (negative) -minorValue else minorValue)
    }

    private val DECIMAL_PATTERN = Regex("-?\\d+(\\.\\d+)?")
}
