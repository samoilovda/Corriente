package com.corriente.data.backup

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

/** T5.1 / F1.3 — ключи статуса последнего запуска автобэкапа. */
class AutoBackupSettingsTest {

    @Test
    fun `defaults - never run yet`() = runBlocking {
        val settings = AutoBackupSettings(FakeAppSettingDao())
        val c = settings.config.first()
        assertNull(c.lastRunAt)
        assertNull(c.lastResult)
        assertEquals(DEFAULT_BACKUP_RETENTION, c.retention)
    }

    @Test
    fun `recordRun stores timestamp and result, visible in config and current`() = runBlocking {
        val settings = AutoBackupSettings(FakeAppSettingDao())
        settings.recordRun(1_700_000_000_000L, "ok")

        val fromFlow = settings.config.first()
        assertEquals(1_700_000_000_000L, fromFlow.lastRunAt)
        assertEquals("ok", fromFlow.lastResult)

        val fromCurrent = settings.current()
        assertEquals(1_700_000_000_000L, fromCurrent.lastRunAt)
        assertEquals("ok", fromCurrent.lastResult)
    }

    @Test
    fun `recordRun overwrites the previous result with an error text`() = runBlocking {
        val settings = AutoBackupSettings(FakeAppSettingDao())
        settings.recordRun(1L, "ok")
        settings.recordRun(2L, "диск переполнен")
        assertEquals("диск переполнен", settings.current().lastResult)
        assertEquals(2L, settings.current().lastRunAt)
    }

    @Test
    fun `setRetention clamps to at least 1`() = runBlocking {
        val settings = AutoBackupSettings(FakeAppSettingDao())
        settings.setRetention(0)
        assertEquals(1, settings.current().retention)
        settings.setRetention(30)
        assertEquals(30, settings.current().retention)
    }
}
