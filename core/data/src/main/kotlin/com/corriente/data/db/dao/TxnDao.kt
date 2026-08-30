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

    @Query("SELECT COUNT(*) FROM txn WHERE import_hash = :importHash")
    suspend fun countByImportHash(importHash: String): Int

    @Query("DELETE FROM txn WHERE import_batch_id = :batchId")
    suspend fun deleteByImportBatch(batchId: String)

    @Query("DELETE FROM txn")
    suspend fun deleteAll()
}
