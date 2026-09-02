package com.corriente.data.applock

import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeAppSettingDao : AppSettingDao {
    val rows = MutableStateFlow<Map<String, String>>(emptyMap())
    override suspend fun set(setting: AppSettingEntity) {
        rows.value = rows.value + (setting.key to setting.value)
    }
    override suspend fun get(key: String): String? = rows.value[key]
    override fun observe(key: String): Flow<String?> = rows.map { it[key] }
    override suspend fun getAll(): List<AppSettingEntity> = rows.value.map { AppSettingEntity(it.key, it.value) }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

/** R5.2: настройки блокировки — режим по умолчанию, запись успешной разблокировки. */
class AppLockSettingsTest {

    @Test
    fun `default mode is OFF, no unlock timestamp yet`() = runBlocking {
        val settings = AppLockSettings(FakeAppSettingDao())
        val c = settings.config.first()
        assertEquals(AppLockMode.OFF, c.mode)
        assertNull(c.lastUnlockAtMs)
        assertEquals(AppLockMode.OFF, settings.current().mode)
    }

    @Test
    fun `setMode is visible in config and current`() = runBlocking {
        val settings = AppLockSettings(FakeAppSettingDao())
        settings.setMode(AppLockMode.AFTER_5_MINUTES)
        assertEquals(AppLockMode.AFTER_5_MINUTES, settings.config.first().mode)
        assertEquals(AppLockMode.AFTER_5_MINUTES, settings.current().mode)
    }

    @Test
    fun `recordUnlock stores the timestamp, later calls overwrite it`() = runBlocking {
        val settings = AppLockSettings(FakeAppSettingDao())
        settings.recordUnlock(1_000L)
        assertEquals(1_000L, settings.current().lastUnlockAtMs)
        settings.recordUnlock(2_000L)
        assertEquals(2_000L, settings.current().lastUnlockAtMs)
    }
}
