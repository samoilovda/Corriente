package com.corriente.money

/**
 * ISO-4217 код валюты, всегда 3 заглавные латинские буквы.
 * Инвариант I-14: список валют не ограничен кодом — это данные, коды не перечисляются enum'ом.
 */
@JvmInline
value class CurrencyCode(val code: String) {
    init {
        require(CODE_PATTERN.matches(code)) { "Invalid ISO-4217 currency code: '$code'" }
    }

    override fun toString(): String = code

    private companion object {
        val CODE_PATTERN = Regex("[A-Z]{3}")
    }
}

/**
 * Метаданные валюты. [minorUnits] — показатель ISO-4217 (сколько десятичных знаков в минорной
 * единице, напр. 2 для USD, 0 для CLP/JPY). [displayScale] — сколько знаков показывать
 * пользователю; может отличаться от [minorUnits] (ARCHITECTURE.md §3.2).
 */
data class Currency(
    val code: CurrencyCode,
    val minorUnits: Int,
    val displayScale: Int,
    val symbol: String,
) {
    init {
        require(minorUnits in 0..4) { "Unsupported minorUnits=$minorUnits for $code" }
        require(displayScale in 0..minorUnits) {
            "displayScale=$displayScale must not exceed minorUnits=$minorUnits for $code"
        }
    }
}
