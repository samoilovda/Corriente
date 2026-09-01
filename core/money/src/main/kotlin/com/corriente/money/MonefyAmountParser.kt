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

    /** Результат мягкого разбора: сумма и признак, что лишние ненулевые дробные знаки округлены. */
    data class Lenient(val minor: Minor, val roundedFromExcessPrecision: Boolean)

    /**
     * @param raw строка вида "49,120", "-1,250.75", "100.001"
     * @param currency валюта, к которой приводится результат (даёт minorUnits для округления)
     * @return сумма в минорных единицах [currency]
     * @throws IllegalArgumentException если строка не число ИЛИ дробных знаков больше, чем у валюты
     *   (тихо округлять нельзя — I-4).
     */
    fun parse(raw: String, currency: Currency): Minor {
        val lenient = parseLenient(raw, currency)
        require(!lenient.roundedFromExcessPrecision) {
            "Amount '$raw' has more fractional digits than ${currency.code} supports " +
                "(minorUnits=${currency.minorUnits}); refusing to silently round"
        }
        return lenient.minor
    }

    /**
     * То же, но лишние ненулевые дробные знаки округляются HALF_UP, а не роняют разбор
     * (импорт: строку с избыточной точностью нельзя потерять, но и молча принять нельзя —
     * planner помечает её NEEDS_REVIEW по флагу [Lenient.roundedFromExcessPrecision]).
     * Бросает только на действительно нечисловой строке.
     */
    fun parseLenient(raw: String, currency: Currency): Lenient {
        val cleaned = raw.trim().replace(",", "")
        require(cleaned.isNotEmpty()) { "Empty amount" }
        require(DECIMAL_PATTERN.matches(cleaned)) { "Not a valid Monefy amount: '$raw'" }

        val negative = cleaned.startsWith("-")
        val unsigned = cleaned.removePrefix("-")
        val parts = unsigned.split(".", limit = 2)
        val integerPart = parts[0]
        val fractionPart = parts.getOrElse(1) { "" }

        val scale = currency.minorUnits
        val kept = fractionPart.take(scale).padEnd(scale, '0')
        val dropped = fractionPart.drop(scale)
        val hasExcess = dropped.any { it != '0' }

        var minorValue = (integerPart + kept).toLong()
        if (hasExcess && dropped.first() in '5'..'9') minorValue += 1 // HALF_UP по первому отброшенному знаку
        return Lenient(Minor(if (negative) -minorValue else minorValue), hasExcess)
    }

    private val DECIMAL_PATTERN = Regex("-?\\d+(\\.\\d+)?")
}
