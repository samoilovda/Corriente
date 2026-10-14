package com.corriente.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Общая папка синхронизации (docs/SYNC_DESIGN.md §3). Пути — относительные, разделитель `/`. Устройство пишет только
 * свои файлы; [write] обязан быть атомарным (временный файл + переименование), чтобы недописанный файл никто не прочёл.
 * Реализации: [FileSyncFolder] (тесты, будущий десктоп), SAF-папка на Android (`:app`).
 */
interface SyncFolder {
    /** Имена файлов и папок в [dir] (`""` — корень); папки нет — пустой список. */
    fun list(dir: String): List<String>
    fun read(path: String): ByteArray?
    fun write(path: String, bytes: ByteArray)
    fun delete(path: String)
}

class FileSyncFolder(private val root: File) : SyncFolder {
    override fun list(dir: String): List<String> = File(root, dir).list()?.toList().orEmpty()
    override fun read(path: String): ByteArray? = File(root, path).takeIf { it.isFile }?.readBytes()
    override fun write(path: String, bytes: ByteArray) {
        val target = File(root, path)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "rename failed: $path" }
        }
    }
    override fun delete(path: String) {
        File(root, path).delete()
    }
}

/** Изменение одной строки: её содержимое целиком (столбец → значение) или надгробие ([row] == null). */
@Serializable
data class RowChange(
    val table: String,
    val pk: String,
    val hlc: String,
    val deleted: Boolean = false,
    val row: JsonObject? = null,
)

/** Пачка изменений одного устройства: `changes/<deviceId>/<seq>.json`. */
@Serializable
data class ChangeFile(
    val formatVersion: Int = FORMAT_VERSION,
    val schemaVersion: Int,
    val deviceId: String,
    val seq: Long,
    val changes: List<RowChange>,
) {
    companion object {
        /** 2 (S5): появились снимки [SnapshotFile]; сам формат пачки не изменился — формат 1 читается как есть. */
        const val FORMAT_VERSION = 2
    }
}

/**
 * Снимок устройства (S5, docs/SYNC_DESIGN.md §7): `snapshots/<deviceId>.json` — всё синхронизируемое состояние, каким
 * оно видно этому устройству (строки с версиями HLC и надгробия), актуальное на [seq] его собственных пачек. Применяется
 * тем же LWW, что и обычные изменения; нужен устройству, которому не хватает уже удалённых пачек.
 */
@Serializable
data class SnapshotFile(
    val formatVersion: Int = ChangeFile.FORMAT_VERSION,
    val schemaVersion: Int,
    val deviceId: String,
    val seq: Long,
    val changes: List<RowChange>,
)

/** Карточка устройства: `devices/<deviceId>.json`. [seen] — до какого `seq` применены файлы каждого устройства (для очистки, S5). */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val schemaVersion: Int,
    val lastSeq: Long,
    val seen: Map<String, Long> = emptyMap(),
    val primary: Boolean = false,
    /** Когда карточка записана (мс). 0 — записана приложением без этого поля: считается «живой» (очистку блокирует). */
    val updatedAt: Long = 0,
)

internal val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal object SyncPaths {
    const val DEVICES = "devices"
    const val CHANGES = "changes"
    const val SNAPSHOTS = "snapshots"
    fun snapshot(id: String) = "$SNAPSHOTS/$id.json"
    fun device(id: String) = "$DEVICES/$id.json"
    fun changesDir(id: String) = "$CHANGES/$id"
    fun changeFile(id: String, seq: Long) = "$CHANGES/$id/${seq.toString().padStart(10, '0')}.json"
    fun seqOf(fileName: String): Long? = fileName.removeSuffix(".json").takeIf { fileName.endsWith(".json") }?.toLongOrNull()
}
