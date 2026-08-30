package com.corriente.data.model

import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money

/**
 * Доменная модель счёта — то, с чем работают ViewModel/UI, в отличие от [AccountEntity],
 * который знает только про Room. [openingBalance] — уже [Money], не голый Long (I-1).
 */
data class Account(
    val id: String,
    val name: String,
    val currency: CurrencyCode,
    val kind: AccountKind,
    val openingBalance: Money,
    val color: Int,
    val icon: String?,
    val displayOrder: Int,
    val isArchived: Boolean,
    val includeInTotal: Boolean,
)

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    currency = CurrencyCode(currencyCode),
    kind = kind,
    openingBalance = Money(Minor(openingBalanceMinor), CurrencyCode(currencyCode)),
    color = color,
    icon = icon,
    displayOrder = displayOrder,
    isArchived = isArchived,
    includeInTotal = includeInTotal,
)

fun Account.toEntity(): AccountEntity {
    require(openingBalance.currency == currency) {
        "Opening balance currency ${openingBalance.currency} does not match account currency $currency"
    }
    return AccountEntity(
        id = id,
        name = name,
        currencyCode = currency.code,
        kind = kind,
        openingBalanceMinor = openingBalance.amount.raw,
        color = color,
        icon = icon,
        displayOrder = displayOrder,
        isArchived = isArchived,
        includeInTotal = includeInTotal,
    )
}
