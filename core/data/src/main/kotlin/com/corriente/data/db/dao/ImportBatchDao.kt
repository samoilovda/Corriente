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
     *
     * A1.7: «осиротевшая» — ещё и без бюджетов, повторяющихся правил и подкатегорий: их FK (NO ACTION)
     * иначе делали откат невозможным. Занятые категории остаются (и сообщаются в
     * [countInUseImportCategoriesForBatch]).
     */
    @Query(
        """
        DELETE FROM category
        WHERE origin = 'IMPORT'
          AND import_batch_id = :batchId
          AND id NOT IN (SELECT DISTINCT category_id FROM txn WHERE category_id IS NOT NULL)
          AND id NOT IN (SELECT category_id FROM budget WHERE category_id IS NOT NULL)
          AND id NOT IN (SELECT category_id FROM recurrence WHERE category_id IS NOT NULL)
          AND id NOT IN (SELECT parent_id FROM category WHERE parent_id IS NOT NULL)
        """
    )
    suspend fun deleteOrphanedImportCategoriesForBatch(batchId: String)

    /** A1.7: сколько IMPORT-категорий батча остаётся после отката, потому что ими пользуются. */
    @Query("SELECT COUNT(*) FROM category WHERE origin = 'IMPORT' AND import_batch_id = :batchId")
    suspend fun countInUseImportCategoriesForBatch(batchId: String): Int

    @Query("UPDATE import_batch SET report_json = :json WHERE id = :id")
    suspend fun updateReport(id: String, json: String)

    /** A3.4: `row_count` — фактически вставленные строки, а не размер плана (дубли пропускаются). */
    @Query("UPDATE import_batch SET row_count = :rowCount WHERE id = :id")
    suspend fun updateRowCount(id: String, rowCount: Int)

    @Query("DELETE FROM import_batch")
    suspend fun deleteAll()
}
