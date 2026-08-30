package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

enum class ImportAliasKind { CATEGORY, ACCOUNT, CURRENCY }

/**
 * Ручной маппинг "значение из чужого приложения → сущность в Corriente"
 * (MONEFY_IMPORT.md §5, п.3). Применяется при импорте и переиспользуется при повторных импортах.
 */
@Entity(
    tableName = "import_alias",
    primaryKeys = ["source_app", "kind", "source_value"],
)
data class ImportAliasEntity(
    @ColumnInfo(name = "source_app")
    val sourceApp: String,
    val kind: ImportAliasKind,
    @ColumnInfo(name = "source_value")
    val sourceValue: String,
    @ColumnInfo(name = "target_id")
    val targetId: String,
)
