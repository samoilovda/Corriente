package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.sumMoney
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class CategoryTotal(val categoryId: String?, val total: Money)

/**
 * Отчёт по категориям за период (T1.8). Работает внутри одной валюты (ADR-012: конвертации
 * нет) — вызывающий выбирает [currency] на экране, по умолчанию ту, где больше всего операций
 * за период. Переводы исключены по построению: [Txn.Transfer] здесь не даёт [CategoryTotal]
 * вообще, потому что у него нет категории (I-11).
 */
fun categoryReport(
    transactions: List<Txn>,
    currency: CurrencyCode,
    period: ClosedRange<LocalDate>,
    kind: ReportKind,
): List<CategoryTotal> {
    val relevant = transactions.filter { it.date in period }
        .mapNotNull { txn ->
            when {
                kind == ReportKind.EXPENSE && txn is Txn.Expense && txn.amount.currency == currency ->
                    txn.categoryId to txn.amount
                kind == ReportKind.INCOME && txn is Txn.Income && txn.amount.currency == currency ->
                    txn.categoryId to txn.amount
                else -> null
            }
        }
    return relevant
        .groupBy({ it.first }, { it.second })
        .map { (categoryId, amounts) -> CategoryTotal(categoryId, amounts.sumMoney()) }
        .sortedByDescending { it.total.amount.raw }
}

enum class ReportKind { EXPENSE, INCOME }

class CategoryReportUseCase(private val txnRepository: TxnRepository) {
    fun observeReport(currency: CurrencyCode, period: ClosedRange<LocalDate>, kind: ReportKind): Flow<List<CategoryTotal>> =
        txnRepository.observeAll().map { categoryReport(it, currency, period, kind) }
}
