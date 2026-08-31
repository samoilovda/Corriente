package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corriente.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM account WHERE is_archived = 0 ORDER BY display_order")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE is_archived = 1 ORDER BY display_order")
    fun observeArchived(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM txn WHERE account_id = :accountId OR to_account_id = :accountId)")
    suspend fun hasTransactions(accountId: String): Boolean

    @Query("DELETE FROM account")
    suspend fun deleteAll()
}
