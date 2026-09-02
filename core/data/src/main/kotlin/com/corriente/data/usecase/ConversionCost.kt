package com.corriente.data.usecase

import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money

/** R3.4: меньше сделок по паре — сравнивать курс не с чем (ADR-013: курсы не берём из сети). */
private const val MIN_DEALS_FOR_ESTIMATE = 3

/** Масштаб [FxDeal.rateMicros] — курс, умноженный на 1e6 (см. `FxReport.kt`). */
private const val RATE_SCALE_MICROS = 1_000_000L

/**
 * R3.4: во что обходится отклонение курса каждой сделки от медианного курса той же пары —
 * развитие T5.4 («мои фактические курсы»). `null` в [cost] значит «недостаточно данных»
 * (меньше [MIN_DEALS_FOR_ESTIMATE] сделок по паре) — это состояние экрана обязан показать как
 * текст, а не как 0.
 *
 * Пара не привязана к направлению: A→B и B→A — одна и та же пара для пользователя (одна и та
 * же операция обмена, просто в разные даты и на разные суммы), поэтому сделки обоих направлений
 * считаются вместе и идут в один и тот же порог [MIN_DEALS_FOR_ESTIMATE] и в одну медиану.
 * [baseCurrency]/[quoteCurrency] — устойчивый порядок валют пары (по коду, лексикографически),
 * не привязанный к тому, в какую сторону была первая сделка.
 */
data class ConversionCostEstimate(
    val baseCurrency: CurrencyCode,
    val quoteCurrency: CurrencyCode,
    val dealCount: Int,
    val cost: Money?,
)

/**
 * Курс сделки, приведённый к единому направлению «[quote] за единицу [base]» — вне зависимости
 * от того, была сама сделка `base → quote` или `quote → base`. Для второго случая курс
 * [FxDeal.rateMicros] уже посчитан как `quote/base`-наоборот (`base` за единицу `quote`),
 * поэтому берём обратную величину той же точности (I-4: округление один раз — здесь целочисленное
 * деление, оценочная величина отчёта, не хранится).
 */
private fun normalizedRateMicros(deal: FxDeal, base: CurrencyCode): Long =
    if (deal.from.currency == base) {
        deal.rateMicros
    } else {
        Math.multiplyExact(RATE_SCALE_MICROS, RATE_SCALE_MICROS) / deal.rateMicros
    }

/**
 * Сумма сделки, реально выраженная в [quote] — для `base → quote` это принятая сумма (`to`),
 * для `quote → base` — отданная (`from`). Это не пересчёт по курсу (ADR-012 такого не разрешает),
 * а выбор той из двух реальных сумм сделки, которая и так в валюте [quote].
 */
private fun quoteAmountMinor(deal: FxDeal, quote: CurrencyCode): Long =
    if (deal.to.currency == quote) deal.to.amount.raw else deal.from.amount.raw

/** Медиана отсортированного по возрастанию списка: середина — либо один элемент, либо среднее двух. */
internal fun medianMicros(values: List<Long>): Long {
    require(values.isNotEmpty()) { "medianMicros: values must not be empty" }
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        Math.addExact(sorted[mid - 1], sorted[mid]) / 2
    }
}

private fun unorderedPairKey(a: CurrencyCode, b: CurrencyCode): Pair<CurrencyCode, CurrencyCode> =
    if (a.code <= b.code) a to b else b to a

/**
 * @param deals сделки T5.4 ([fxDeals] — уже только межвалютные переводы), любой период —
 * фильтр по году (или другому окну) применяет вызывающий (ViewModel), это не забота чистой
 * функции сравнения.
 */
fun conversionCost(deals: List<FxDeal>): List<ConversionCostEstimate> =
    deals.groupBy { unorderedPairKey(it.from.currency, it.to.currency) }
        .map { (pairKey, pairDeals) ->
            val (base, quote) = pairKey
            if (pairDeals.size < MIN_DEALS_FOR_ESTIMATE) {
                ConversionCostEstimate(base, quote, pairDeals.size, cost = null)
            } else {
                val rates = pairDeals.map { normalizedRateMicros(it, base) }
                val median = medianMicros(rates)
                // «Сколько стоили конвертации» — это цена отклонения от медианы в любую сторону
                // (и переплата, и недоплата — обе означают, что курс сделки был не «типичным» для
                // вас), поэтому суммируем модуль отклонения каждой сделки, а не знаковую сумму,
                // где переплаты и недоплаты гасили бы друг друга и прятали реальный разброс.
                var totalMinor = 0L
                pairDeals.forEachIndexed { i, deal ->
                    val deviation = Math.subtractExact(rates[i], median)
                    val quoteMinor = quoteAmountMinor(deal, quote)
                    val dealCostMinor = kotlin.math.abs(Math.multiplyExact(deviation, quoteMinor) / median)
                    totalMinor = Math.addExact(totalMinor, dealCostMinor)
                }
                ConversionCostEstimate(base, quote, pairDeals.size, Money(Minor(totalMinor), quote))
            }
        }
        .sortedWith(compareBy({ it.baseCurrency.code }, { it.quoteCurrency.code }))
