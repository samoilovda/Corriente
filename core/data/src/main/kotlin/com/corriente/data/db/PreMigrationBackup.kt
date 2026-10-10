package com.corriente.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.corriente.data.backup.namesToPrune
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * I-20: «Перед применением миграции автоматически создаётся копия файла БД». Room не даёт хука
 * «перед миграцией», поэтому проверяем сами — до открытия БД сравниваем `PRAGMA user_version`
 * файла с целевой версией схемы и, если файл старее, копируем его (вместе с `-wal`/`-shm`).
 *
 * F1.4: [copyDatabaseFile] переиспользуется для копии «до восстановления из бэкапа».
 */
object PreMigrationBackup {

    private const val KEEP = 3
    private val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /** @return созданная копия основного файла БД, либо null если копировать было нечего. */
    fun runIfNeeded(context: Context, dbName: String, targetVersion: Int, now: Date = Date()): File? {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return null
        val currentVersion = readUserVersion(dbFile) ?: return null
        if (currentVersion >= targetVersion) return null
        return copyDatabaseFile(context, dbName, "v$currentVersion", now)
    }

    /**
     * Копирует файл БД (и `-wal`/`-shm`) в `databases/pre-migration/` под именем
     * `<dbName>.<reason>.<штамп>`, подрезая старые копии до [KEEP]. F1.4.
     *
     * A2.15: перед копированием WAL сбрасывается в основной файл (`PRAGMA wal_checkpoint(TRUNCATE)` по отдельному
     * соединению) — копия основного файла содержит последнюю запись и не зависит от «живого» `-wal`, который мог
     * измениться посреди копирования. Если БД занята и checkpoint не удался, копируются все три файла, как раньше.
     *
     * @return копия основного файла, либо null если файла БД нет.
     */
    fun copyDatabaseFile(context: Context, dbName: String, reason: String, now: Date = Date()): File? =
        copyDatabaseFile(context.getDatabasePath(dbName), reason, now)

    internal fun copyDatabaseFile(dbFile: File, reason: String, now: Date = Date()): File? {
        val dbName = dbFile.name
        if (!dbFile.exists()) return null
        checkpointWal(dbFile)

        val dir = File(dbFile.parentFile, "pre-migration").apply { mkdirs() }
        val stamp = STAMP.format(now)
        var mainCopy: File? = null
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(dbFile.path + suffix)
            if (!src.exists()) continue
            val dst = File(dir, "$dbName.$reason.$stamp$suffix")
            src.copyTo(dst, overwrite = true)
            if (suffix.isEmpty()) mainCopy = dst
        }

        val mains = dir.list()
            ?.filter { it.startsWith("$dbName.") && !it.endsWith("-wal") && !it.endsWith("-shm") }
            ?: emptyList()
        namesToPrune(mains, KEEP).forEach { name ->
            File(dir, name).delete()
            File(dir, "$name-wal").delete()
            File(dir, "$name-shm").delete()
        }
        return mainCopy
    }

    /** Копия из `databases/pre-migration/`, пригодная для восстановления (A2.15). */
    data class Copy(val name: String, val reason: String, val stamp: String, val sizeBytes: Long)

    private const val PENDING_MARKER = "restore-pending"

    /** Копии БД (основные файлы), новые первыми. */
    fun listCopies(context: Context, dbName: String): List<Copy> =
        listCopies(context.getDatabasePath(dbName))

    internal fun listCopies(dbFile: File): List<Copy> {
        val dir = File(dbFile.parentFile, "pre-migration")
        val prefix = dbFile.name + "."
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith("-wal") && !it.name.endsWith("-shm") }
            .mapNotNull { f ->
                val rest = f.name.removePrefix(prefix)
                val dot = rest.lastIndexOf('.')
                if (dot <= 0) null else Copy(f.name, rest.substring(0, dot), rest.substring(dot + 1), f.length())
            }
            .sortedByDescending { it.stamp }
    }

    /**
     * Откладывает восстановление копии [copyName]: БД открыта, файл подменять нельзя, поэтому пишется метка, а
     * подмену делает [applyPendingRestore] при следующем запуске процесса до открытия БД.
     * @return false, если такой копии нет.
     */
    fun stageRestore(context: Context, dbName: String, copyName: String): Boolean =
        stageRestore(context.getDatabasePath(dbName), copyName)

    internal fun stageRestore(dbFile: File, copyName: String): Boolean {
        val dir = File(dbFile.parentFile, "pre-migration")
        if (listCopies(dbFile).none { it.name == copyName }) return false
        File(dir, PENDING_MARKER).writeText(copyName)
        return true
    }

    /**
     * Подмена БД выбранной копией, если [stageRestore] оставил метку. Текущая БД перед этим сохраняется как копия
     * `pre-restore-copy`, поэтому и это восстановление обратимо. Вызывать до первого открытия БД.
     * @return true, если подмена выполнена.
     */
    fun applyPendingRestore(context: Context, dbName: String): Boolean =
        applyPendingRestore(context.getDatabasePath(dbName))

    internal fun applyPendingRestore(dbFile: File): Boolean {
        val dir = File(dbFile.parentFile, "pre-migration")
        val marker = File(dir, PENDING_MARKER)
        if (!marker.exists()) return false
        val name = marker.readText().trim()
        val source = File(dir, name)
        if (name.isEmpty() || name.contains('/') || !source.exists()) {
            marker.delete()
            return false
        }
        // Выбранную копию забираем во временный файл до снимка текущей БД: снимок подрезает старые копии.
        val staged = File(dbFile.path + ".restoring")
        source.copyTo(staged, overwrite = true)
        val stagedWal = File(source.path + "-wal").takeIf { it.exists() }
            ?.let { it.copyTo(File(dbFile.path + ".restoring-wal"), overwrite = true) }
        copyDatabaseFile(dbFile, "pre-restore-copy")
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        staged.copyTo(dbFile, overwrite = true)
        staged.delete()
        stagedWal?.let { it.copyTo(File(dbFile.path + "-wal"), overwrite = true); it.delete() }
        marker.delete()
        return true
    }

    private fun checkpointWal(file: File) {
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        }
    }

    private fun readUserVersion(file: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }.getOrNull()
}
