package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.corriente.data.db.entity.ImportBatchEntity

@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity)

    @Delete
    suspend fun delete(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batch ORDER BY imported_at DESC")
    suspend fun getAll(): List<ImportBatchEntity>

    /** Категории, созданные этим импортом, на которые больше не ссылается ни одна операция (I-19). */
    @Query(
        """
        DELETE FROM category
        WHERE origin = 'IMPORT'
          AND id NOT IN (SELECT DISTINCT category_id FROM txn WHERE category_id IS NOT NULL)
        """
    )
    suspend fun deleteOrphanedImportCategories()
}
