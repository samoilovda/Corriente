package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountKind { CASH, CARD, SAVINGS, DEBT }

/**
 * Счёт (ARCHITECTURE.md §3.2). Инвариант I-23: валюта счёта неизменна после первой операции —
 * это правило enforced в репозитории (:core:data use-case уровня, не здесь), потому что Room
 * не умеет условных ограничений "нельзя изменить поле, если у сущности есть связанные записи".
 */
@Entity(
    tableName = "account",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currency_code"],
        ),
    ],
    indices = [Index("currency_code")],
)
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    val kind: AccountKind,
    @ColumnInfo(name = "opening_balance_minor", defaultValue = "0")
    val openingBalanceMinor: Long = 0,
    val color: Int,
    val icon: String? = null,
    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "include_in_total", defaultValue = "1")
    val includeInTotal: Boolean = true,
)
