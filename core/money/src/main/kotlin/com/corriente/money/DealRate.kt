package com.corriente.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Курс валютной сделки — только вычисляется, никогда не хранится (I-7а, I-12). Единственный
 * источник истины перевода — обе суммы; этот объект выводит из них неявный курс для показа
 * («1 USD = 86.95 RUB») и пересчитывает одну сумму по введённому вручную курсу.
 *
 * [BigDecimal] — транзитный тип внутри деления (I-1 разрешает его именно так); наружу отдаётся
 * либо строка курса, либо [Minor] с округлением РОВНО ОДИН РАЗ до минорных единиц приёмника (I-4).
 */
object DealRate {

    /** Точность внутреннего представления курса — с запасом, до показа не округляем. */
    private const val RATE_SCALE = 10

    private fun Minor.toMajor(currency: Currency): BigDecimal =
        BigDecimal(raw).movePointLeft(currency.minorUnits)

    /**
     * Сколько единиц [to] за одну единицу [from]. `null`, если сумма-источник нулевая
     * (курс не определён).
     */
    fun rate(from: Money, fromCurrency: Currency, to: Money, toCurrency: Currency): BigDecimal? {
        require(from.currency == fromCurrency.code && to.currency == toCurrency.code) {
            "DealRate.rate: currency/amount mismatch"
        }
        val fromMajor = from.amount.toMajor(fromCurrency)
        if (fromMajor.signum() == 0) return null
        return to.amount.toMajor(toCurrency).divide(fromMajor, RATE_SCALE, RoundingMode.HALF_UP)
    }

    /** Пересчёт суммы-приёмника из суммы-источника по курсу [rate] (единиц to за 1 единицу from). */
    fun applyRate(from: Money, fromCurrency: Currency, rate: BigDecimal, toCurrency: Currency): Minor {
        require(from.currency == fromCurrency.code) { "DealRate.applyRate: currency mismatch" }
        val toMajor = from.amount.toMajor(fromCurrency).multiply(rate)
        return Minor(toMajor.movePointRight(toCurrency.minorUnits).setScale(0, RoundingMode.HALF_UP).toLong())
    }

    /** Обратный пересчёт: сумма-источник из суммы-приёмника по тому же курсу. */
    fun applyRateInverse(to: Money, toCurrency: Currency, rate: BigDecimal, fromCurrency: Currency): Minor {
        require(to.currency == toCurrency.code) { "DealRate.applyRateInverse: currency mismatch" }
        if (rate.signum() == 0) return Minor(0)
        val fromMajor = to.amount.toMajor(toCurrency).divide(rate, RATE_SCALE, RoundingMode.HALF_UP)
        return Minor(fromMajor.movePointRight(fromCurrency.minorUnits).setScale(0, RoundingMode.HALF_UP).toLong())
    }

    /** Строка вида «1 USD = 86.95 RUB»; `null`, если курс не определён. */
    fun format(from: Money, fromCurrency: Currency, to: Money, toCurrency: Currency): String? {
        val rate = rate(from, fromCurrency, to, toCurrency) ?: return null
        // до 6 знаков — хватает и для крупных курсов (86.95), и для мелких (0.011501); хвостовые нули убираем.
        val shown = rate.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return "1 ${fromCurrency.code.code} = $shown ${toCurrency.code.code}"
    }
}
