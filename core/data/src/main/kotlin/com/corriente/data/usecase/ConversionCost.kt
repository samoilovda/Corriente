package com.corriente.data.usecase

import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** R3.4: меньше сделок по паре — сравнивать курс не с чем (ADR-013: курсы не берём из сети). */
private const val MIN_DEALS_FOR_ESTIMATE = 3

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
 * Курс сделки, приведённый к единому направлению «[quote] за единицу [base]» — вне зависимости от того, была сама
 * сделка `base → quote` или `quote → base` (для второй берётся обратная величина). A2.14: считается в [BigDecimal] —
 * это отношение сумм, не деньги; итог округляется один раз до минорных единиц (I-4).
 */
private fun normalizedRate(deal: FxDeal, base: CurrencyCode): BigDecimal =
    if (deal.from.currency == base) deal.rate else BigDecimal.ONE.divide(deal.rate, MathContext.DECIMAL128)

/**
 * Сумма сделки, реально выраженная в [quote] — для `base → quote` это принятая сумма (`to`),
 * для `quote → base` — отданная (`from`). Это не пересчёт по курсу (ADR-012 такого не разрешает),
 * а выбор той из двух реальных сумм сделки, которая и так в валюте [quote].
 */
private fun quoteAmountMinor(deal: FxDeal, quote: CurrencyCode): Long =
    if (deal.to.currency == quote) deal.to.amount.raw else deal.from.amount.raw

/** Медиана: середина отсортированного списка — либо один элемент, либо среднее двух. */
internal fun median(values: List<BigDecimal>): BigDecimal {
    require(values.isNotEmpty()) { "median: values must not be empty" }
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        sorted[mid - 1].add(sorted[mid]).divide(BigDecimal(2), MathContext.DECIMAL128)
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
            if (pairDeals.size < MIN_DEALS_FOR_ESTIMATE || pairDeals.any { it.rate.signum() <= 0 }) {
                ConversionCostEstimate(base, quote, pairDeals.size, cost = null)
            } else {
                val rates = pairDeals.map { normalizedRate(it, base) }
                val median = median(rates)
                // «Сколько стоили конвертации» — это цена отклонения от медианы в любую сторону
                // (и переплата, и недоплата — обе означают, что курс сделки был не «типичным» для
                // вас), поэтому суммируем модуль отклонения каждой сделки, а не знаковую сумму,
                // где переплаты и недоплаты гасили бы друг друга и прятали реальный разброс.
                var total = BigDecimal.ZERO
                pairDeals.forEachIndexed { i, deal ->
                    total = total.add(
                        rates[i].subtract(median).abs().multiply(BigDecimal(quoteAmountMinor(deal, quote)))
                            .divide(median, MathContext.DECIMAL128),
                    )
                }
                // Округление ровно один раз, до минорных единиц (I-4); не помещается в Long — «недостаточно данных».
                val totalMinor = runCatching { total.setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
                ConversionCostEstimate(
                    base, quote, pairDeals.size,
                    cost = totalMinor?.let { Money(Minor(it), quote) },
                )
            }
        }
        .sortedWith(compareBy({ it.baseCurrency.code }, { it.quoteCurrency.code }))
