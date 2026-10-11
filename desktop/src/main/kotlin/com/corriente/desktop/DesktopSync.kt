package com.corriente.desktop

import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.sync.DeviceInfo
import com.corriente.data.sync.FileSyncFolder
import com.corriente.data.sync.SyncEngine
import com.corriente.data.sync.SyncIntegrityException
import com.corriente.data.sync.SyncStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetAddress

/** Что показывает экран «Синхронизация» десктопа. */
data class DesktopSyncStatus(
    val enabled: Boolean = false,
    val primary: Boolean = false,
    val folder: String? = null,
    val deviceName: String? = null,
    val lastRunAt: Long? = null,
    /** null — последний обмен успешен (или ещё не было). */
    val lastError: String? = null,
    val deferred: Int = 0,
    val newerSchema: Boolean = false,
)

/**
 * Синхронизация на компьютере — тот же движок ([SyncEngine]), папка Syncthing — обычная папка ([FileSyncFolder]).
 * Обмен: при запуске, через 5 с после локальной записи и раз в минуту (на компьютере это дёшево — никакой батареи).
 * Состояние — в `app_setting` (`sync.*`, привязаны к устройству), как на Android.
 */
@OptIn(FlowPreview::class)
class DesktopSync(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    /** Копия данных перед подключением «с заменой» (полный JSON-бэкап в папку данных). */
    private val beforeReplace: suspend () -> Unit = {},
) {

    private val settings = db.appSettingDao()
    private val mutex = Mutex()

    val status: Flow<DesktopSyncStatus> = combine(
        db.syncDao().observeState(),
        combine(settings.observe(KEY_FOLDER), settings.observe(SyncEngine.KEY_DEVICE_NAME), settings.observe(SyncEngine.KEY_PRIMARY)) { f, n, p -> Triple(f, n, p) },
        combine(settings.observe(KEY_LAST_RUN), settings.observe(KEY_LAST_ERROR), settings.observe(KEY_NEWER)) { r, e, n -> Triple(r, e, n) },
        db.syncDao().observeDeferredCount(),
    ) { state, (folder, name, primary), (run, error, newer), deferred ->
        DesktopSyncStatus(
            enabled = state?.enabled == true,
            primary = primary == "true",
            folder = folder,
            deviceName = name,
            lastRunAt = run?.toLongOrNull(),
            lastError = error?.takeIf { it.isNotEmpty() },
            deferred = deferred,
            newerSchema = newer == "true",
        )
    }

    val defaultDeviceName: String =
        runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: System.getProperty("os.name")

    /** Запускает фоновый обмен: при старте, после записи и раз в минуту. */
    fun start() {
        scope.launch { runOnce() }
        combine(db.syncDao().observeState().map { it?.enabled == true }, db.syncDao().observeOutboxCount()) { e, n -> e && n > 0 }
            .filter { it }
            .debounce(AFTER_WRITE_MS)
            .onEach { runOnce() }
            .launchIn(scope)
        scope.launch {
            while (true) {
                delay(PERIOD_MS)
                runOnce()
            }
        }
    }

    private suspend fun engine(): SyncEngine? = settings.get(KEY_FOLDER)?.let { SyncEngine(db, FileSyncFolder(File(it))) }

    suspend fun setFolder(path: String) = settings.set(AppSettingEntity(KEY_FOLDER, path))

    suspend fun devices(): List<DeviceInfo> = runCatching { engine()?.devices() }.getOrNull().orEmpty()

    suspend fun ownDeviceId(): String? = engine()?.deviceId()

    suspend fun hasLocalData(): Boolean = engine()?.hasLocalData() ?: false

    suspend fun startHere(name: String) = mutex.withLock {
        val engine = requireNotNull(engine())
        engine.enable(name, primary = true, baseline = true)
        runLocked(engine)
    }

    /** [replace] — своё удаляется перед подключением; null — своих данных нет; false — объединить. */
    suspend fun join(name: String, replace: Boolean?) = mutex.withLock {
        val engine = requireNotNull(engine())
        if (replace == true) {
            beforeReplace()
            engine.wipeLocalData()
        }
        engine.enable(name, primary = false, baseline = replace == false)
        runLocked(engine)
    }

    suspend fun disable() = mutex.withLock { db.syncDao().setState(SyncStateEntity(enabled = false)) }

    suspend fun runOnce(): String? = mutex.withLock {
        val engine = engine() ?: return@withLock null
        if (!engine.isEnabled()) return@withLock null
        runLocked(engine)
    }

    private suspend fun runLocked(engine: SyncEngine): String? {
        val outcome = runCatching { engine.sync() }
        val error = outcome.exceptionOrNull()?.let { e ->
            when (e) {
                is SyncIntegrityException -> "Изменения с других устройств не применены: данные не прошли бы проверку целостности."
                is java.io.IOException, is SecurityException -> "Нет доступа к общей папке: ${e.message}"
                else -> e.message ?: e.javaClass.simpleName
            }
        }
        settings.set(AppSettingEntity(KEY_LAST_RUN, System.currentTimeMillis().toString()))
        settings.set(AppSettingEntity(KEY_LAST_ERROR, error.orEmpty()))
        settings.set(AppSettingEntity(KEY_NEWER, (outcome.getOrNull()?.newerSchemaDevices?.isNotEmpty() == true).toString()))
        return error
    }

    suspend fun isEnabled(): Boolean = db.syncDao().observeState().first()?.enabled == true

    companion object {
        private const val KEY_FOLDER = "sync.folder_path"
        private const val KEY_LAST_RUN = "sync.last_run_at"
        private const val KEY_LAST_ERROR = "sync.last_error"
        private const val KEY_NEWER = "sync.newer_schema"
        private const val AFTER_WRITE_MS = 5_000L
        private const val PERIOD_MS = 60_000L
    }
}
