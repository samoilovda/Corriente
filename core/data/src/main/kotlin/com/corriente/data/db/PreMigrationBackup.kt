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
 * Схема всё ещё v1, поэтому копия сейчас не создаётся ни разу — механизм готов заранее
 * (как и `MigrationTestHelper` в T0.4), чтобы первая же миграция была безопасной.
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

        val dir = File(dbFile.parentFile, "pre-migration").apply { mkdirs() }
        val stamp = STAMP.format(now)
        var mainCopy: File? = null
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(dbFile.path + suffix)
            if (!src.exists()) continue
            val dst = File(dir, "$dbName.v$currentVersion.$stamp$suffix")
            src.copyTo(dst, overwrite = true)
            if (suffix.isEmpty()) mainCopy = dst
        }

        val mains = dir.list()?.filter { it.startsWith("$dbName.v") && !it.endsWith("-wal") && !it.endsWith("-shm") }
            ?: emptyList()
        namesToPrune(mains, KEEP).forEach { name ->
            File(dir, name).delete()
            File(dir, "$name-wal").delete()
            File(dir, "$name-shm").delete()
        }
        return mainCopy
    }

    private fun readUserVersion(file: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }.getOrNull()
}
