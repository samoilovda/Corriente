package com.corriente.data.model

import com.corriente.data.db.entity.BudgetEntity
import com.corriente.data.db.entity.BudgetPeriod
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import java.time.LocalDate

/**
 * Бюджет по категории (R2.3) — доменная модель. [amount] уже несёт свою валюту (I-1/ADR-012):
 * отдельного поля "валюта" нет, ровно как у [Account.openingBalance] — источник истины один.
 */
data class Budget(
    val id: String,
    val categoryId: String?,
    val amount: Money,
    val period: BudgetPeriod,
    val startsOn: LocalDate,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    amount = Money(Minor(amountMinor), CurrencyCode(currencyCode)),
    period = period,
    startsOn = LocalDate.parse(startsOn),
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    currencyCode = amount.currency.code,
    amountMinor = amount.amount.raw,
    period = period,
    startsOn = startsOn.toString(),
)
