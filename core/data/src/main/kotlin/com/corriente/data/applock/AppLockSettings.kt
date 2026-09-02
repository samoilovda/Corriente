package com.corriente.data.applock

import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Настройки блокировки приложения (R5.2) — в той же key-value таблице `app_setting`, что и
 * [com.corriente.data.backup.AutoBackupSettings]: без миграции схемы (I-20 требует
 * `Migration`-объекта на любое изменение схемы, а нового столбца тут не нужно).
 */
class AppLockSettings(private val dao: AppSettingDao) {

    val config: Flow<AppLockConfig> = combine(dao.observe(KEY_MODE), dao.observe(KEY_LAST_UNLOCK_AT)) { mode, lastUnlock ->
        AppLockConfig(
            mode = mode?.let(::parseMode) ?: AppLockMode.OFF,
            lastUnlockAtMs = lastUnlock?.toLongOrNull(),
        )
    }

    suspend fun current(): AppLockConfig = AppLockConfig(
        mode = dao.get(KEY_MODE)?.let(::parseMode) ?: AppLockMode.OFF,
        lastUnlockAtMs = dao.get(KEY_LAST_UNLOCK_AT)?.toLongOrNull(),
    )

    suspend fun setMode(mode: AppLockMode) = dao.set(AppSettingEntity(KEY_MODE, mode.name))

    /** Записывается после каждого успешного прохождения биометрии/PIN. */
    suspend fun recordUnlock(atEpochMs: Long) = dao.set(AppSettingEntity(KEY_LAST_UNLOCK_AT, atEpochMs.toString()))

    private fun parseMode(raw: String): AppLockMode = runCatching { AppLockMode.valueOf(raw) }.getOrDefault(AppLockMode.OFF)

    private companion object {
        const val KEY_MODE = "app_lock.mode"
        const val KEY_LAST_UNLOCK_AT = "app_lock.last_unlock_at"
    }
}
