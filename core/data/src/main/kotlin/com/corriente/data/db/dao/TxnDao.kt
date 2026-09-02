package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corriente.data.db.entity.TxnEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface TxnDao {
    @Insert
    suspend fun insert(txn: TxnEntity)

    @Insert
    suspend fun insertAll(txns: List<TxnEntity>)

    @Update
    suspend fun update(txn: TxnEntity)

    @Delete
    suspend fun delete(txn: TxnEntity)

    @Query("SELECT * FROM txn WHERE account_id = :accountId OR to_account_id = :accountId ORDER BY date DESC, created_at DESC")
    fun observeForAccount(accountId: String): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn ORDER BY date DESC, created_at DESC")
    fun observeAll(): Flow<List<TxnEntity>>

    /** Операции за период (F2.1) — список/отчёт подписываются на диапазон, а не на всю таблицу. */
    @Query(
        "SELECT * FROM txn WHERE date >= :from AND date <= :to ORDER BY date DESC, created_at DESC",
    )
    fun observeRange(from: String, to: String): Flow<List<TxnEntity>>

    /**
     * Сумма движений по каждому счёту в его валюте (F2.1). Расход/перевод-источник — со знаком
     * минус, доход/перевод-приёмник — плюс; всё в самом SQL. Знак валюты счёта единствен (I-15),
     * поэтому `GROUP BY account_id, currency_code` даёт одну строку на счёт.
     */
    @Query(
        """
        SELECT account_id AS accountId, currency_code AS currencyCode, SUM(delta) AS deltaMinor FROM (
            SELECT account_id, currency_code,
                CASE kind
                    WHEN 'INCOME' THEN amount_minor
                    WHEN 'EXPENSE' THEN -amount_minor
                    WHEN 'TRANSFER' THEN -amount_minor
                END AS delta
            FROM txn
            UNION ALL
            SELECT to_account_id AS account_id, to_currency_code AS currency_code, to_amount_minor AS delta
            FROM txn WHERE kind = 'TRANSFER'
        )
        GROUP BY account_id, currency_code
        """,
    )
    fun observeAccountDeltas(): Flow<List<AccountDeltaRow>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun getById(id: String): TxnEntity?

    /** Есть ли в БД хоть одна операция — чтобы отличать «окно пусто» от «операций нет вовсе» (F2.1/F2.5). */
    @Query("SELECT EXISTS(SELECT 1 FROM txn)")
    fun observeAnyExist(): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM txn WHERE import_hash = :importHash")
    suspend fun countByImportHash(importHash: String): Int

    @Query("DELETE FROM txn WHERE import_batch_id = :batchId")
    suspend fun deleteByImportBatch(batchId: String)

    @Query("DELETE FROM txn")
    suspend fun deleteAll()
}
