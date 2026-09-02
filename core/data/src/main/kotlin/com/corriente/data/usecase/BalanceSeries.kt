package com.corriente.data.usecase

import com.corriente.data.model.Account
import com.corriente.data.model.Txn
import com.corriente.money.Money
import java.time.LocalDate

/** R3.2: остаток счёта на конец [date] — точка графика «динамика по счёту». */
data class BalancePoint(val date: LocalDate, val balance: Money)

/**
 * R3.2: остаток счёта [account] день за днём внутри [range] — накопительный итог поверх
 * [accountBalance] (T1.7), рисуется на `ReportCharts` (Canvas, как T5.3). Начальный остаток
 * периода — это [accountBalance], посчитанный по всей истории **до** [range] (включая
 * `account.openingBalance`), то есть первая точка ряда уже учитывает всё, что было раньше
 * (критерий приёмки R3.2), а не только счёт с нуля с начала диапазона.
 *
 * Переводы отражены с правильным знаком — той же веткой [applyToBalance], что и в
 * [accountBalance]: `-` для счёта-источника, `+` для счёта-приёмника (одна и та же логика,
 * не дублированная и не переизобретённая здесь).
 */
fun balanceSeries(account: Account, transactions: List<Txn>, range: ClosedRange<LocalDate>): List<BalancePoint> {
    var running = accountBalance(account, transactions.filter { it.date < range.start })
    val byDate = transactions.filter { it.date in range }.groupBy { it.date }

    val points = mutableListOf<BalancePoint>()
    var date = range.start
    while (date <= range.endInclusive) {
        byDate[date]?.forEach { txn -> running = applyToBalance(running, account, txn) }
        points += BalancePoint(date, running)
        date = date.plusDays(1)
    }
    return points
}
