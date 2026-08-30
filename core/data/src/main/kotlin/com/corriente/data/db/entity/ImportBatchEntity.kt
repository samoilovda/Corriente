package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Один запуск импорта (ARCHITECTURE.md §3.2, MONEFY_IMPORT.md §5). Откат импорта — это
 * `DELETE FROM txn WHERE import_batch_id = ?` плюс чистка осиротевших категорий
 * с origin = IMPORT (I-19).
 */
@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "source_app")
    val sourceApp: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "imported_at")
    val importedAt: Long,
    @ColumnInfo(name = "row_count")
    val rowCount: Int,
    /** Отчёт dry-run: что создано, что помечено NEEDS_REVIEW, что не распарсилось. */
    @ColumnInfo(name = "report_json")
    val reportJson: String,
)
