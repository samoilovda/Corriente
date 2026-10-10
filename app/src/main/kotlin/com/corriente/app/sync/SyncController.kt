package com.corriente.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.PreMigrationBackup
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.sync.DeviceInfo
import com.corriente.data.sync.SyncEngine
import com.corriente.data.sync.SyncIntegrityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Что показывает экран «Синхронизация» и пункт в «Настройках». */
data class SyncStatus(
    val enabled: Boolean = false,
    val primary: Boolean = false,
    val folderUri: String? = null,
    val deviceName: String? = null,
    val lastRunAt: Long? = null,
    /** null — последний обмен успешен (или ещё не было); иначе [SyncError]. */
    val lastError: SyncError? = null,
    val lastApplied: Int = 0,
    val deferred: Int = 0,
    /** Имена устройств, чьи данные новее схемы этого приложения. */
    val newerSchemaDevices: List<String> = emptyList(),
) {
    /** Импорт Monefy и откат импорта — только на основном устройстве (docs/SYNC_DESIGN.md §10). */
    val importAllowed: Boolean get() = !enabled || primary
}

sealed interface SyncError {
    data object FolderUnavailable : SyncError
    /** Применение откатано: данные не прошли проверку целостности. */
    data object Integrity : SyncError
    data class Other(val message: String) : SyncError
}

/** Как подключить устройство, на котором уже есть свои данные (§6). */
enum class JoinMode { MERGE, REPLACE }

/**
 * Синхронизация на Android: связывает движок ([SyncEngine], `:core:data`) с SAF-папкой, хранит статус в `app_setting`
 * (ключи `sync.*` привязаны к устройству) и не даёт двум обменам идти одновременно (воркер + кнопка).
 */
class SyncController(private val context: Context, private val db: AppDatabase) {

    private val settings = db.appSettingDao()
    private val mutex = Mutex()

    val status: Flow<SyncStatus> = combine(
        db.syncDao().observeState(),
        combine(settings.observe(KEY_TREE), settings.observe(SyncEngine.KEY_DEVICE_NAME), settings.observe(SyncEngine.KEY_PRIMARY)) { t, n, p -> Triple(t, n, p) },
        combine(settings.observe(KEY_LAST_RUN), settings.observe(KEY_LAST_ERROR), settings.observe(KEY_LAST_APPLIED)) { r, e, a -> Triple(r, e, a) },
        settings.observe(KEY_NEWER),
        db.syncDao().observeDeferredCount(),
    ) { state, (tree, name, primary), (run, error, applied), newer, deferred ->
        SyncStatus(
            enabled = state?.enabled == true,
            primary = primary == "true",
            folderUri = tree,
            deviceName = name,
            lastRunAt = run?.toLongOrNull(),
            lastError = decodeError(error),
            lastApplied = applied?.toIntOrNull() ?: 0,
            deferred = deferred,
            newerSchemaDevices = newer?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
        )
    }

    val enabled: Flow<Boolean> = db.syncDao().observeState().map { it?.enabled == true }

    val outboxCount: Flow<Int> = db.syncDao().observeOutboxCount()

    private suspend fun treeUri(): Uri? = settings.get(KEY_TREE)?.toUri()

    private suspend fun engine(): SyncEngine? = treeUri()?.let { SyncEngine(db, SafSyncFolder(context, it)) }

    /** Новая папка: постоянное разрешение берёт вызывающий; прежнее освобождаем (лимит грантов конечен, как в A3.13). */
    suspend fun setFolder(uri: Uri) {
        val previous = settings.get(KEY_TREE)
        settings.set(AppSettingEntity(KEY_TREE, uri.toString()))
        if (previous != null && previous != uri.toString()) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(previous.toUri(), flags) }
        }
    }

    /** «Начать синхронизацию с этого устройства»: основное устройство, выкладывает все свои данные. */
    suspend fun startHere(deviceName: String) = mutex.withLock {
        val engine = requireNotNull(engine()) { "folder not chosen" }
        engine.enable(deviceName, primary = true, baseline = true)
        runLocked(engine)
    }

    /** На устройстве уже есть свои данные? Тогда при подключении нужно спросить, что с ними делать. */
    suspend fun hasLocalData(): Boolean = withContext(Dispatchers.IO) { engine()?.hasLocalData() ?: false }

    /**
     * «Подключиться к синхронизации». [mode] == null — своих данных нет. [JoinMode.REPLACE] — свои данные удаляются (до
     * этого — копия БД в «Копиях перед обновлением и восстановлением»); [JoinMode.MERGE] — свои данные тоже выкладываются.
     */
    suspend fun join(deviceName: String, mode: JoinMode?) = mutex.withLock {
        val engine = requireNotNull(engine()) { "folder not chosen" }
        if (mode == JoinMode.REPLACE) {
            withContext(Dispatchers.IO) {
                runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
                PreMigrationBackup.copyDatabaseFile(context, AppDatabase.DB_NAME, REASON_BEFORE_JOIN)
            }
            engine.wipeLocalData()
        }
        engine.enable(deviceName, primary = false, baseline = mode == JoinMode.MERGE)
        runLocked(engine)
    }

    suspend fun disable() = mutex.withLock { engine()?.disable() ?: db.syncDao().setState(com.corriente.data.sync.SyncStateEntity(enabled = false)) }

    /** Один обмен (воркер, кнопка, открытие приложения). Ошибки не бросает — записывает в статус. */
    suspend fun runOnce(): SyncError? = mutex.withLock {
        val engine = engine() ?: return@withLock null
        if (!engine.isEnabled()) return@withLock null
        runLocked(engine)
    }

    suspend fun devices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        runCatching { engine()?.devices() }.getOrNull().orEmpty()
    }

    suspend fun ownDeviceId(): String? = engine()?.deviceId()

    private suspend fun runLocked(engine: SyncEngine): SyncError? = withContext(Dispatchers.IO) {
        val outcome = runCatching { engine.sync() }
        val error = outcome.exceptionOrNull()?.let { e ->
            when (e) {
                is SyncIntegrityException -> SyncError.Integrity
                is SecurityException, is java.io.FileNotFoundException, is IllegalArgumentException -> SyncError.FolderUnavailable
                else -> SyncError.Other(e.message ?: e.javaClass.simpleName)
            }
        }
        val result = outcome.getOrNull()
        val newer = result?.newerSchemaDevices.orEmpty().let { ids ->
            if (ids.isEmpty()) emptyList() else engine.devices().filter { it.deviceId in ids }.map { it.name.ifBlank { it.deviceId.take(8) } }
        }
        settings.set(AppSettingEntity(KEY_LAST_RUN, System.currentTimeMillis().toString()))
        settings.set(AppSettingEntity(KEY_LAST_ERROR, encodeError(error)))
        settings.set(AppSettingEntity(KEY_LAST_APPLIED, (result?.applied ?: 0).toString()))
        settings.set(AppSettingEntity(KEY_NEWER, newer.joinToString("\n")))
        error
    }

    private fun encodeError(e: SyncError?): String = when (e) {
        null -> ""
        SyncError.FolderUnavailable -> "folder"
        SyncError.Integrity -> "integrity"
        is SyncError.Other -> "other:${e.message}"
    }

    private fun decodeError(raw: String?): SyncError? = when {
        raw.isNullOrEmpty() -> null
        raw == "folder" -> SyncError.FolderUnavailable
        raw == "integrity" -> SyncError.Integrity
        else -> SyncError.Other(raw.removePrefix("other:"))
    }

    companion object {
        /** Причина копии БД перед подключением с заменой данных (видна в «Копиях перед обновлением и восстановлением»). */
        const val REASON_BEFORE_JOIN = "pre-sync-join"
        private const val KEY_TREE = "sync.tree_uri"
        private const val KEY_LAST_RUN = "sync.last_run_at"
        private const val KEY_LAST_ERROR = "sync.last_error"
        private const val KEY_LAST_APPLIED = "sync.last_applied"
        private const val KEY_NEWER = "sync.newer_schema"
    }
}
