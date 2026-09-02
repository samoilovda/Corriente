package com.corriente.data.db.dao

import androidx.room.ColumnInfo

/**
 * Проекция агрегата: сумма всех движений по счёту в его валюте (F2.1). Считается в SQL
 * (`GROUP BY`), а не полным сканом `txn` в памяти — баланс = `opening + deltaMinor`.
 */
data class AccountDeltaRow(
    @ColumnInfo(name = "accountId") val accountId: String,
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    @ColumnInfo(name = "deltaMinor") val deltaMinor: Long,
)
