package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** R2.4: как считать следующую дату — конкретный день месяца или каждые N дней. */
enum class RecurrenceRuleType { DAY_OF_MONTH, EVERY_N_DAYS }

/**
 * Шаблон повторяющейся операции (R2.4, ARCHITECTURE.md §3.2): расход или доход (перевод не
 * поддерживается — у него две стороны и это усложнение, которого сценарий "аренда/подписка/
 * зарплата" не требует), плюс правило повторения и указатель на следующую дату.
 *
 * [nextRunOn] — следующая ещё не материализованная дата по этому правилу (ISO-8601). Ключевое
 * решение R2.4: воркер создаёт операции только за даты `<= сегодня`, никогда не авансом —
 * [nextRunOn] у самой свежей операции всегда либо в будущем, либо равен ей самой, пока воркер
 * её не обработает.
 */
@Entity(
    tableName = "recurrence",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"]),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currency_code"]),
    ],
    indices = [
        Index("account_id"),
        Index("category_id"),
        Index("currency_code"),
    ],
)
data class RecurrenceEntity(
    @PrimaryKey
    val id: String,
    val kind: TxnKind,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    val note: String?,
    @ColumnInfo(name = "rule_type")
    val ruleType: RecurrenceRuleType,
    /** 1..31, только для [RecurrenceRuleType.DAY_OF_MONTH]; в коротком месяце клэмпится вниз. */
    @ColumnInfo(name = "day_of_month")
    val dayOfMonth: Int?,
    /** >= 1, только для [RecurrenceRuleType.EVERY_N_DAYS]. */
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int?,
    @ColumnInfo(name = "next_run_on")
    val nextRunOn: String,
    /** Операция, созданная последним запуском воркера — для экрана «Повторяющиеся» (R2.4). */
    @ColumnInfo(name = "last_created_txn_id")
    val lastCreatedTxnId: String?,
)
