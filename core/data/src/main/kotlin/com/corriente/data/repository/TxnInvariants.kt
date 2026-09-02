package com.corriente.data.repository

import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind

/**
 * Правила записи операции, которые Room не проверяет на уровне схемы (I-1, I-11, I-15).
 * Чистая функция и единственное место, где эти правила enforced: её вызывают и
 * [TxnRepository], и импорт Monefy (`MonefyImportRepository`) — F1.2, чтобы будущая правка
 * правил действовала на оба пути записи, а не только на ручной ввод.
 *
 * @param accountCurrency код валюты счёта по его id; `null` — счёта нет.
 * @throws IllegalArgumentException при любом нарушении (в т.ч. `require`).
 */
fun requireValidTxn(entity: TxnEntity, accountCurrency: (String) -> String?) {
    require(entity.amountMinor > 0) {
        "Txn ${entity.id}: amountMinor must be > 0, sign comes from kind (I-1)"
    }
    val fromCurrency = accountCurrency(entity.accountId)
        ?: throw IllegalArgumentException("Txn ${entity.id}: account ${entity.accountId} not found")
    require(entity.currencyCode == fromCurrency) {
        "Txn ${entity.id}: currency ${entity.currencyCode} != account currency $fromCurrency (I-15)"
    }
    when (entity.kind) {
        TxnKind.TRANSFER -> {
            val toAccountId = requireNotNull(entity.toAccountId) { "Transfer ${entity.id}: no toAccountId (I-11)" }
            val toAmount = requireNotNull(entity.toAmountMinor) { "Transfer ${entity.id}: no toAmountMinor (I-11)" }
            val toCurrency = requireNotNull(entity.toCurrencyCode) { "Transfer ${entity.id}: no toCurrencyCode (I-11)" }
            require(toAmount > 0) { "Transfer ${entity.id}: toAmountMinor must be > 0 (I-1)" }
            require(entity.categoryId == null) { "Transfer ${entity.id}: must have no category (I-11)" }
            require(entity.accountId != toAccountId) { "Transfer ${entity.id}: cannot transfer an account to itself" }
            val actualToCurrency = accountCurrency(toAccountId)
                ?: throw IllegalArgumentException("Transfer ${entity.id}: account $toAccountId not found")
            require(toCurrency == actualToCurrency) {
                "Transfer ${entity.id}: to-currency $toCurrency != account currency $actualToCurrency (I-15)"
            }
        }

        TxnKind.EXPENSE, TxnKind.INCOME -> require(
            entity.toAccountId == null && entity.toAmountMinor == null && entity.toCurrencyCode == null,
        ) {
            "Txn ${entity.id}: expense/income must have no second side (I-11)"
        }
    }
}
