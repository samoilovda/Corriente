package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.corriente.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * `abstract class`, а не `interface`, ради [merge]: слияние обязано быть атомарным
 * (переназначить операции и удалить категорию в одной транзакции), а `@Transaction` над
 * методом с телом работает только в классе.
 */
@Dao
abstract class CategoryDao {
    @Insert
    abstract suspend fun insert(category: CategoryEntity): Long

    @Update
    abstract suspend fun update(category: CategoryEntity)

    @Delete
    abstract suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE is_archived = 0 ORDER BY display_order")
    abstract fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE is_archived = 1 ORDER BY display_order")
    abstract fun observeArchived(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    abstract suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM txn WHERE category_id = :categoryId)")
    abstract suspend fun hasTransactions(categoryId: String): Boolean

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :parentId")
    abstract suspend fun childCount(parentId: String): Int

    @Query("UPDATE txn SET category_id = :intoId WHERE category_id = :fromId")
    abstract suspend fun reassignTransactions(fromId: String, intoId: String)

    /** Слияние (T1.4): операции категории [fromId] переезжают на [intoId], исходная удаляется. */
    @Transaction
    open suspend fun merge(fromId: String, intoId: String) {
        reassignTransactions(fromId, intoId)
        val from = getById(fromId) ?: return
        delete(from)
    }

    @Query("DELETE FROM category")
    abstract suspend fun deleteAll()
}
