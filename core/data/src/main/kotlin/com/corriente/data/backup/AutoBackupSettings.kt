package com.corriente.data.backup

import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AutoBackupConfig(
    val enabled: Boolean = false,
    val treeUri: String? = null,
    val retention: Int = DEFAULT_BACKUP_RETENTION,
    /** Эпоха в мс последнего запуска воркера, null — ещё ни разу не выполнялся (F1.3). */
    val lastRunAt: Long? = null,
    /** `"ok"` при успехе, иначе текст ошибки; null — запусков не было. */
    val lastResult: String? = null,
)

/** Допустимые значения ротации на экране (F1.3). */
val BACKUP_RETENTION_CHOICES = listOf(3, 7, 14, 30)

/**
 * Настройки автобэкапа (T5.1) — в key-value таблице `app_setting`, чтобы не заводить миграцию
 * схемы. F1.3 добавил ключи статуса последнего запуска — тоже без миграции.
 */
class AutoBackupSettings(private val dao: AppSettingDao) {

    val config: Flow<AutoBackupConfig> = combine(
        dao.observe(KEY_ENABLED),
        dao.observe(KEY_TREE_URI),
        dao.observe(KEY_RETENTION),
        dao.observe(KEY_LAST_RUN_AT),
        dao.observe(KEY_LAST_RESULT),
    ) { values ->
        AutoBackupConfig(
            enabled = values[0] == "true",
            treeUri = (values[1])?.takeIf { it.isNotBlank() },
            retention = values[2]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_BACKUP_RETENTION,
            lastRunAt = values[3]?.toLongOrNull(),
            lastResult = values[4]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun current(): AutoBackupConfig = AutoBackupConfig(
        enabled = dao.get(KEY_ENABLED) == "true",
        treeUri = dao.get(KEY_TREE_URI)?.takeIf { it.isNotBlank() },
        retention = dao.get(KEY_RETENTION)?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_BACKUP_RETENTION,
        lastRunAt = dao.get(KEY_LAST_RUN_AT)?.toLongOrNull(),
        lastResult = dao.get(KEY_LAST_RESULT)?.takeIf { it.isNotBlank() },
    )

    suspend fun setEnabled(enabled: Boolean) = dao.set(AppSettingEntity(KEY_ENABLED, enabled.toString()))
    suspend fun setTreeUri(uri: String) = dao.set(AppSettingEntity(KEY_TREE_URI, uri))
    suspend fun setRetention(n: Int) = dao.set(AppSettingEntity(KEY_RETENTION, n.coerceAtLeast(1).toString()))

    /** Записать итог запуска воркера (F1.3). */
    suspend fun recordRun(atEpochMs: Long, result: String) {
        dao.set(AppSettingEntity(KEY_LAST_RUN_AT, atEpochMs.toString()))
        dao.set(AppSettingEntity(KEY_LAST_RESULT, result))
    }

    private companion object {
        const val KEY_ENABLED = "auto_backup.enabled"
        const val KEY_TREE_URI = "auto_backup.tree_uri"
        const val KEY_RETENTION = "auto_backup.retention"
        const val KEY_LAST_RUN_AT = "auto_backup.last_run_at"
        const val KEY_LAST_RESULT = "auto_backup.last_result"
    }
}
