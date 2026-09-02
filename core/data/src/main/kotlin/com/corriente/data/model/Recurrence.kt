package com.corriente.data.model

import com.corriente.data.db.entity.RecurrenceEntity
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import java.time.LocalDate

/**
 * Повторяющаяся операция (R2.4) — доменная модель. [kind] только EXPENSE/INCOME (перевод не
 * поддерживается, RecurrenceRepository это проверяет). [rule] — уже разобранное правило
 * ([RecurrenceRule]), а не пара nullable-полей "тип + число", как в [RecurrenceEntity].
 */
data class Recurrence(
    val id: String,
    val kind: TxnKind,
    val accountId: String,
    val categoryId: String?,
    val amount: Money,
    val note: String?,
    val rule: RecurrenceRule,
    val nextRunOn: LocalDate,
    val lastCreatedTxnId: String?,
)

fun RecurrenceEntity.toDomain(): Recurrence = Recurrence(
    id = id,
    kind = kind,
    accountId = accountId,
    categoryId = categoryId,
    amount = Money(Minor(amountMinor), CurrencyCode(currencyCode)),
    note = note,
    rule = when (ruleType) {
        RecurrenceRuleType.DAY_OF_MONTH -> RecurrenceRule.DayOfMonth(requireNotNull(dayOfMonth) { "day_of_month missing for $id" })
        RecurrenceRuleType.EVERY_N_DAYS -> RecurrenceRule.EveryNDays(requireNotNull(intervalDays) { "interval_days missing for $id" })
    },
    nextRunOn = LocalDate.parse(nextRunOn),
    lastCreatedTxnId = lastCreatedTxnId,
)

fun Recurrence.toEntity(): RecurrenceEntity = RecurrenceEntity(
    id = id,
    kind = kind,
    accountId = accountId,
    categoryId = categoryId,
    amountMinor = amount.amount.raw,
    currencyCode = amount.currency.code,
    note = note,
    ruleType = when (rule) {
        is RecurrenceRule.DayOfMonth -> RecurrenceRuleType.DAY_OF_MONTH
        is RecurrenceRule.EveryNDays -> RecurrenceRuleType.EVERY_N_DAYS
    },
    dayOfMonth = (rule as? RecurrenceRule.DayOfMonth)?.day,
    intervalDays = (rule as? RecurrenceRule.EveryNDays)?.intervalDays,
    nextRunOn = nextRunOn.toString(),
    lastCreatedTxnId = lastCreatedTxnId,
)
