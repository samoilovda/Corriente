package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corriente.data.db.entity.RecurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceDao {
    @Insert
    suspend fun insert(recurrence: RecurrenceEntity)

    @Update
    suspend fun update(recurrence: RecurrenceEntity)

    @Delete
    suspend fun delete(recurrence: RecurrenceEntity)

    @Query("SELECT * FROM recurrence ORDER BY next_run_on")
    fun observeAll(): Flow<List<RecurrenceEntity>>

    @Query("SELECT * FROM recurrence")
    suspend fun getAll(): List<RecurrenceEntity>

    @Query("SELECT * FROM recurrence WHERE id = :id")
    suspend fun getById(id: String): RecurrenceEntity?

    /** R2.4: воркер сдвигает указатель и запоминает последнюю созданную операцию за один шаг. */
    @Query("UPDATE recurrence SET next_run_on = :nextRunOn, last_created_txn_id = :lastCreatedTxnId WHERE id = :id")
    suspend fun recordRun(id: String, nextRunOn: String, lastCreatedTxnId: String?)

    @Query("DELETE FROM recurrence")
    suspend fun deleteAll()
}
