package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corriente.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE is_archived = 0 ORDER BY display_order")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    /** Слияние: все операции категории [fromId] переезжают на [intoId], исходная категория удаляется. */
    @Query("UPDATE txn SET category_id = :intoId WHERE category_id = :fromId")
    suspend fun reassignTransactions(fromId: String, intoId: String)
}
