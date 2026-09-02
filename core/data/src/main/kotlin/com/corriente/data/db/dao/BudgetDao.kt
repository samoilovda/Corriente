package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corriente.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budget ORDER BY starts_on DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Query("SELECT * FROM budget")
    suspend fun getAll(): List<BudgetEntity>

    @Query("DELETE FROM budget")
    suspend fun deleteAll()
}
