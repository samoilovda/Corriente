package com.corriente.data.model

import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import java.time.LocalDate

/**
 * Доменная модель операции — sealed вместо одного класса с кучей nullable-полей (как
 * [TxnEntity]), чтобы состояние "Expense с заполненным toAccountId" было невозможно
 * представить, а не просто "не должно происходить" (I-11: категория и перевод взаимоисключающи).
 */
sealed interface Txn {
    val id: String
    val date: LocalDate
    val createdAt: Long
    val updatedAt: Long
    val note: String?
    val importBatchId: String?
    val importHash: String?

    data class Expense(
        override val id: String,
        override val date: LocalDate,
        override val createdAt: Long,
        override val updatedAt: Long,
        val accountId: String,
        /** Всегда положительна (I-1) — знак определяется тем, что это Expense, а не значением. */
        val amount: Money,
        val categoryId: String?,
        override val note: String? = null,
        override val importBatchId: String? = null,
        override val importHash: String? = null,
    ) : Txn

    data class Income(
        override val id: String,
        override val date: LocalDate,
        override val createdAt: Long,
        override val updatedAt: Long,
        val accountId: String,
        val amount: Money,
        val categoryId: String?,
        override val note: String? = null,
        override val importBatchId: String? = null,
        override val importHash: String? = null,
    ) : Txn

    /** I-7а: источник истины — обе суммы; курс сделки выводится из них, нигде не хранится. */
    data class Transfer(
        override val id: String,
        override val date: LocalDate,
        override val createdAt: Long,
        override val updatedAt: Long,
        val fromAccountId: String,
        val fromAmount: Money,
        val toAccountId: String,
        val toAmount: Money,
        override val note: String? = null,
        override val importBatchId: String? = null,
        override val importHash: String? = null,
    ) : Txn
}

fun TxnEntity.toDomain(): Txn {
    val parsedDate = LocalDate.parse(date)
    return when (kind) {
        TxnKind.EXPENSE -> Txn.Expense(
            id = id,
            date = parsedDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
            accountId = accountId,
            amount = Money(Minor(amountMinor), CurrencyCode(currencyCode)),
            categoryId = categoryId,
            note = note,
            importBatchId = importBatchId,
            importHash = importHash,
        )
        TxnKind.INCOME -> Txn.Income(
            id = id,
            date = parsedDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
            accountId = accountId,
            amount = Money(Minor(amountMinor), CurrencyCode(currencyCode)),
            categoryId = categoryId,
            note = note,
            importBatchId = importBatchId,
            importHash = importHash,
        )
        TxnKind.TRANSFER -> {
            checkNotNull(toAccountId) { "Transfer $id has no toAccountId" }
            checkNotNull(toAmountMinor) { "Transfer $id has no toAmountMinor" }
            checkNotNull(toCurrencyCode) { "Transfer $id has no toCurrencyCode" }
            Txn.Transfer(
                id = id,
                date = parsedDate,
                createdAt = createdAt,
                updatedAt = updatedAt,
                fromAccountId = accountId,
                fromAmount = Money(Minor(amountMinor), CurrencyCode(currencyCode)),
                toAccountId = toAccountId,
                toAmount = Money(Minor(toAmountMinor), CurrencyCode(toCurrencyCode)),
                note = note,
                importBatchId = importBatchId,
                importHash = importHash,
            )
        }
    }
}

fun Txn.toEntity(): TxnEntity = when (this) {
    is Txn.Expense -> TxnEntity(
        id = id,
        kind = TxnKind.EXPENSE,
        date = date.toString(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        accountId = accountId,
        amountMinor = amount.amount.raw,
        currencyCode = amount.currency.code,
        categoryId = categoryId,
        note = note,
        importBatchId = importBatchId,
        importHash = importHash,
    )
    is Txn.Income -> TxnEntity(
        id = id,
        kind = TxnKind.INCOME,
        date = date.toString(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        accountId = accountId,
        amountMinor = amount.amount.raw,
        currencyCode = amount.currency.code,
        categoryId = categoryId,
        note = note,
        importBatchId = importBatchId,
        importHash = importHash,
    )
    is Txn.Transfer -> TxnEntity(
        id = id,
        kind = TxnKind.TRANSFER,
        date = date.toString(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        accountId = fromAccountId,
        amountMinor = fromAmount.amount.raw,
        currencyCode = fromAmount.currency.code,
        toAccountId = toAccountId,
        toAmountMinor = toAmount.amount.raw,
        toCurrencyCode = toAmount.currency.code,
        categoryId = null,
        note = note,
        importBatchId = importBatchId,
        importHash = importHash,
    )
}
