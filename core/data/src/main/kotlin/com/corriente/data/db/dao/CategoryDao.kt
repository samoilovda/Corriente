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

    @Query("SELECT * FROM category ORDER BY display_order")
    abstract fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    abstract suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM txn WHERE category_id = :categoryId)")
    abstract suspend fun hasTransactions(categoryId: String): Boolean

    /** A1.7: на категорию ссылается бюджет — иначе FK `budget.category_id` (NO ACTION) не даст её удалить. */
    @Query("SELECT EXISTS(SELECT 1 FROM budget WHERE category_id = :categoryId)")
    abstract suspend fun hasBudgets(categoryId: String): Boolean

    /** A1.7: на категорию ссылается повторяющееся правило (FK `recurrence.category_id`). */
    @Query("SELECT EXISTS(SELECT 1 FROM recurrence WHERE category_id = :categoryId)")
    abstract suspend fun hasRecurrences(categoryId: String): Boolean

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :parentId")
    abstract suspend fun childCount(parentId: String): Int

    @Query("UPDATE txn SET category_id = :intoId WHERE category_id = :fromId")
    abstract suspend fun reassignTransactions(fromId: String, intoId: String)

    /**
     * A1.7: бюджеты [fromId], у которых в [intoId] уже есть бюджет в той же валюте, удаляются — остаётся
     * бюджет целевой категории (два бюджета на пару «категория, валюта» неоднозначны).
     */
    @Query(
        """
        DELETE FROM budget WHERE category_id = :fromId
          AND currency_code IN (SELECT currency_code FROM budget WHERE category_id = :intoId)
        """,
    )
    abstract suspend fun dropDuplicateBudgets(fromId: String, intoId: String)

    @Query("UPDATE budget SET category_id = :intoId WHERE category_id = :fromId")
    abstract suspend fun reassignBudgets(fromId: String, intoId: String)

    @Query("UPDATE recurrence SET category_id = :intoId WHERE category_id = :fromId")
    abstract suspend fun reassignRecurrences(fromId: String, intoId: String)

    /**
     * Слияние (T1.4): операции, бюджеты и повторяющиеся правила категории [fromId] переезжают на [intoId]
     * (A1.7; при конфликте бюджетов в одной валюте остаётся бюджет [intoId]), исходная удаляется.
     */
    @Transaction
    open suspend fun merge(fromId: String, intoId: String) {
        reassignTransactions(fromId, intoId)
        dropDuplicateBudgets(fromId, intoId)
        reassignBudgets(fromId, intoId)
        reassignRecurrences(fromId, intoId)
        val from = getById(fromId) ?: return
        delete(from)
    }

    @Query("DELETE FROM category")
    abstract suspend fun deleteAll()
}
