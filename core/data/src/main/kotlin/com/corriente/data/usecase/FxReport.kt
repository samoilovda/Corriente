package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.DealRate
import com.corriente.money.Money
import java.math.RoundingMode
import java.time.LocalDate

/**
 * T5.4: отчёт «мои фактические курсы обмена». Единственный источник курсов в приложении —
 * собственные межвалютные переводы (ADR-013, I-24: сети нет). Курс каждой сделки выводится
 * из двух сумм и нигде не хранится (I-7а, I-12).
 *
 * [rateMicros] — курс, умноженный на 1_000_000 и округлённый до целого: пригодная для
 * построения графика величина. Это не деньги (I-1 про Money), а отношение сумм.
 */
data class FxDeal(
    val txnId: String,
    val date: LocalDate,
    val from: Money,
    val to: Money,
    val rateLabel: String,
    val rateMicros: Long,
)

data class FxPair(
    val from: CurrencyCode,
    val to: CurrencyCode,
    val deals: List<FxDeal>,
)

fun fxDeals(
    transactions: List<Txn>,
    currencyByCode: Map<CurrencyCode, Currency>,
): List<FxPair> {
    fun meta(code: CurrencyCode) = currencyByCode[code] ?: Currency(code, 2, 2, code.code)

    val deals = transactions
        .filterIsInstance<Txn.Transfer>()
        .filter { it.fromAmount.currency != it.toAmount.currency }
        .mapNotNull { txn ->
            val fromMeta = meta(txn.fromAmount.currency)
            val toMeta = meta(txn.toAmount.currency)
            val rate = DealRate.rate(txn.fromAmount, fromMeta, txn.toAmount, toMeta) ?: return@mapNotNull null
            val label = DealRate.format(txn.fromAmount, fromMeta, txn.toAmount, toMeta) ?: return@mapNotNull null
            FxDeal(
                txnId = txn.id,
                date = txn.date,
                from = txn.fromAmount,
                to = txn.toAmount,
                rateLabel = label,
                rateMicros = rate.movePointRight(6).setScale(0, RoundingMode.HALF_UP).toLong(),
            )
        }

    return deals
        .groupBy { it.from.currency to it.to.currency }
        .map { (pair, list) -> FxPair(pair.first, pair.second, list.sortedBy { it.date }) }
        .sortedWith(compareBy({ it.from.code }, { it.to.code }))
}
