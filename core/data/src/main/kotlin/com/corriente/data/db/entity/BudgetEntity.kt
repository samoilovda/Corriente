package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Пока единственный вариант (R2.3) — «раз в месяц, начиная с [BudgetEntity.startsOn]». */
enum class BudgetPeriod { MONTH }

/**
 * Бюджет по категории (R2.3, ARCHITECTURE.md §3.2). [categoryId] == null — бюджет «на всё»
 * (сумма всех категорий), а не «на все деньги сразу»: он всё равно принадлежит ровно одной
 * валюте (ADR-012, I-8) — бюджета, объединяющего валюты, не существует в принципе, поэтому
 * здесь нет отдельного «а на что" поля, кроме категории.
 */
@Entity(
    tableName = "budget",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"]),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currency_code"]),
    ],
    indices = [
        Index("category_id"),
        Index("currency_code"),
    ],
)
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    val period: BudgetPeriod,
    /** Начало действия бюджета, ISO-8601 'YYYY-MM-DD' — тот же формат, что [TxnEntity.date]. */
    @ColumnInfo(name = "starts_on")
    val startsOn: String,
)
