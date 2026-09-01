package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import java.time.LocalDate
import java.time.YearMonth

data class MonthPoint(val month: YearMonth, val total: Money)

/**
 * T5.3: ряд «расходы (или доходы) по месяцам внутри одной валюты». Конвертации нет (I-8) —
 * ряд всегда в [currency]. Переводы исключены (I-11). Суммы уже посчитаны здесь; график
 * получает готовые [Money], `Float` появляется только на уровне координат.
 *
 * @param monthsBack сколько месяцев показать, включая месяц [anchor].
 */
fun monthlySeries(
    transactions: List<Txn>,
    currency: CurrencyCode,
    kind: ReportKind,
    anchor: LocalDate,
    monthsBack: Int,
): List<MonthPoint> {
    require(monthsBack >= 1) { "monthsBack must be >= 1, got $monthsBack" }
    val end = YearMonth.from(anchor)
    val months = (monthsBack - 1 downTo 0).map { end.minusMonths(it.toLong()) }

    val totals = HashMap<YearMonth, Long>()
    for (txn in transactions) {
        val amount = when {
            kind == ReportKind.EXPENSE && txn is Txn.Expense && txn.amount.currency == currency -> txn.amount
            kind == ReportKind.INCOME && txn is Txn.Income && txn.amount.currency == currency -> txn.amount
            else -> null
        } ?: continue
        val ym = YearMonth.from(txn.date)
        if (ym !in months) continue
        totals[ym] = Math.addExact(totals[ym] ?: 0L, amount.amount.raw)
    }

    return months.map { ym -> MonthPoint(ym, Money(Minor(totals[ym] ?: 0L), currency)) }
}
