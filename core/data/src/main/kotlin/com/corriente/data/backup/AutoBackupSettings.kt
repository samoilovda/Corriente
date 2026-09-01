package com.corriente.data.backup

import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AutoBackupConfig(
    val enabled: Boolean = false,
    val treeUri: String? = null,
    val retention: Int = DEFAULT_BACKUP_RETENTION,
)

/**
 * Настройки автобэкапа (T5.1) — в key-value таблице `app_setting`, чтобы не заводить миграцию
 * схемы (её после этапа 3 нельзя без явного согласования, BUILD_PLAN §8).
 */
class AutoBackupSettings(private val dao: AppSettingDao) {

    val config: Flow<AutoBackupConfig> = combine(
        dao.observe(KEY_ENABLED),
        dao.observe(KEY_TREE_URI),
        dao.observe(KEY_RETENTION),
    ) { enabled, treeUri, retention ->
        AutoBackupConfig(
            enabled = enabled == "true",
            treeUri = treeUri?.takeIf { it.isNotBlank() },
            retention = retention?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_BACKUP_RETENTION,
        )
    }

    suspend fun current(): AutoBackupConfig = AutoBackupConfig(
        enabled = dao.get(KEY_ENABLED) == "true",
        treeUri = dao.get(KEY_TREE_URI)?.takeIf { it.isNotBlank() },
        retention = dao.get(KEY_RETENTION)?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_BACKUP_RETENTION,
    )

    suspend fun setEnabled(enabled: Boolean) = dao.set(AppSettingEntity(KEY_ENABLED, enabled.toString()))
    suspend fun setTreeUri(uri: String) = dao.set(AppSettingEntity(KEY_TREE_URI, uri))
    suspend fun setRetention(n: Int) = dao.set(AppSettingEntity(KEY_RETENTION, n.coerceAtLeast(1).toString()))

    private companion object {
        const val KEY_ENABLED = "auto_backup.enabled"
        const val KEY_TREE_URI = "auto_backup.tree_uri"
        const val KEY_RETENTION = "auto_backup.retention"
    }
}
