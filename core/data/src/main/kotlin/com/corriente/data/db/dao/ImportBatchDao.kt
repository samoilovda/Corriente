package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.corriente.data.db.entity.ImportBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity)

    @Delete
    suspend fun delete(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batch ORDER BY imported_at DESC")
    suspend fun getAll(): List<ImportBatchEntity>

    @Query("SELECT * FROM import_batch ORDER BY imported_at DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    /**
     * F1.5: IMPORT-категории именно [batchId], на которые больше не ссылается ни одна операция.
     * Раньше чистило категории всех батчей — откат одного импорта задевал чужие.
     */
    @Query(
        """
        DELETE FROM category
        WHERE origin = 'IMPORT'
          AND import_batch_id = :batchId
          AND id NOT IN (SELECT DISTINCT category_id FROM txn WHERE category_id IS NOT NULL)
        """
    )
    suspend fun deleteOrphanedImportCategoriesForBatch(batchId: String)

    @Query("DELETE FROM import_batch")
    suspend fun deleteAll()
}
