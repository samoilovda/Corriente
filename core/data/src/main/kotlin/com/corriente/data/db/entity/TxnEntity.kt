package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnKind { EXPENSE, INCOME, TRANSFER }

/**
 * Операция (ARCHITECTURE.md §3.2) — ядро схемы. Одна строка на любую операцию, включая
 * переводы (инвариант I-10/I-7а: перевод не может существовать "половинкой", у него ровно
 * одна строка с обеими суммами).
 *
 * Инварианты, которые Room НЕ проверяет на уровне схемы и которые обязан проверять
 * репозиторий/use-case перед вставкой (docs/INVARIANTS.md):
 *  - I-1: [amountMinor] всегда > 0; знак операции определяется [kind], а не значением.
 *  - [kind] == TRANSFER тогда и только тогда, когда заполнены [toAccountId]/[toAmountMinor]/
 *    [toCurrencyCode], и [categoryId] в этом случае обязан быть null (I-11).
 *  - I-15/I-23: [currencyCode] всегда равен валюте [accountId] на момент вставки.
 * (В оригинальной схеме ARCHITECTURE.md эти правила — обычные SQLite CHECK; Room 2.8 не имеет
 * стабильного публичного API для CHECK constraints на уровне @Entity, поэтому они перенесены
 * в код репозитория — см. TxnRepository, который появится на этапе 1.)
 */
@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"]),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["to_account_id"]),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currency_code"]),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["to_currency_code"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"]),
        ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id"], childColumns = ["import_batch_id"]),
    ],
    indices = [
        Index("date"),
        Index(value = ["account_id", "date"]),
        Index(value = ["category_id", "date"]),
        Index(value = ["to_account_id", "date"]),
        Index(value = ["import_hash"], unique = true, name = "ux_txn_import_hash"),
    ],
)
data class TxnEntity(
    @PrimaryKey
    val id: String,
    val kind: TxnKind,
    /** Локальная дата операции, ISO-8601 'YYYY-MM-DD' (без времени и часового пояса). */
    val date: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    // Заполняются только при kind == TRANSFER.
    @ColumnInfo(name = "to_account_id")
    val toAccountId: String? = null,
    @ColumnInfo(name = "to_amount_minor")
    val toAmountMinor: Long? = null,
    @ColumnInfo(name = "to_currency_code")
    val toCurrencyCode: String? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "import_batch_id")
    val importBatchId: String? = null,
    /** Натуральный ключ строки CSV при импорте — идемпотентность повторного импорта (I-19). */
    @ColumnInfo(name = "import_hash")
    val importHash: String? = null,
)
