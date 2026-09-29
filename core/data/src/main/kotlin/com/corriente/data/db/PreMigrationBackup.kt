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
    fun copyDatabaseFile(context: Context, dbName: String, reason: String, now: Date = Date()): File? {
        val dbFile = context.getDatabasePath(dbName)
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
