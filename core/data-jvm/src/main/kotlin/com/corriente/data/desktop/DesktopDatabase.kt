package com.corriente.data.desktop

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.corriente.data.db.AppDatabase
import com.corriente.data.seed.DEFAULT_CATEGORIES
import com.corriente.data.seed.ISO_CURRENCIES
import com.corriente.data.sync.SyncTriggers
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * База десктопа: тот же [AppDatabase] (версия — [AppDatabase.SCHEMA_VERSION]), файл SQLite через встроенный драйвер. При
 * создании файла — тот же засев,
 * что на Android (`seedCallback`): справочник ISO-4217 (активны RUB и USD), типовые категории с детерминированными id
 * (`seed-…`, I-27 — на телефоне те же, синхронизация сольёт их) и триггеры захвата изменений.
 *
 * Десктоп начинается со схемы 8: его миграции — [DESKTOP_MIGRATIONS] (`DesktopMigrations.kt`), каждая следующая версия схемы
 * обязана добавить миграцию и туда. Перед миграцией файл копируется (I-20, [copyBeforeMigration]).
 */
object DesktopDatabase {

    fun open(file: File): AppDatabase {
        file.parentFile?.mkdirs()
        copyBeforeMigration(file)
        return Room.databaseBuilder<AppDatabase>(name = file.absolutePath).addMigrations(*DESKTOP_MIGRATIONS).configure()
    }

    /**
     * I-20: файл старой версии схемы копируется до того, как Room применит миграции, — в `backups/` рядом с базой
     * (`pre-migration-v<версия>-<время>.db`). Новый файл (версия 0) и файл текущей версии не копируются.
     * @return копия или null, если копировать не нужно.
     */
    fun copyBeforeMigration(file: File, now: Long = System.currentTimeMillis()): File? {
        if (!file.isFile) return null
        val version = BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            val v = connection.prepare("PRAGMA user_version").use { st -> if (st.step()) st.getLong(0).toInt() else 0 }
            // WAL сливаем в основной файл, иначе копия одного файла потеряла бы последние записи.
            if (v in 1 until AppDatabase.SCHEMA_VERSION) {
                connection.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { st -> st.step() }
            }
            v
        }
        if (version == 0 || version >= AppDatabase.SCHEMA_VERSION) return null
        val dir = File(file.parentFile, "backups").apply { mkdirs() }
        val copy = File(dir, "pre-migration-v$version-$now.db")
        file.copyTo(copy, overwrite = true)
        return copy
    }

    /** Для тестов. */
    fun inMemory(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().configure()

    private fun RoomDatabase.Builder<AppDatabase>.configure(): AppDatabase =
        setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) = seed(connection)
            })
            .build()

    /** Room вызывает onCreate уже внутри своей транзакции создания схемы — своя не нужна. */
    private fun seed(connection: SQLiteConnection) {
        connection.prepare(
            "INSERT INTO currency(code, minor_units, display_scale, symbol, is_active, display_order) VALUES (?, ?, ?, ?, ?, 0)",
        ).use { st ->
            ISO_CURRENCIES.forEach { c ->
                st.reset()
                st.bindText(1, c.code)
                st.bindLong(2, c.minorUnits.toLong())
                st.bindLong(3, c.minorUnits.toLong())
                st.bindText(4, c.symbol)
                st.bindLong(5, if (c.code == "RUB" || c.code == "USD") 1 else 0)
                st.step()
            }
        }
        connection.prepare(
            """
            INSERT INTO category(id, name, kind, parent_id, color, icon, origin, display_order, is_archived, import_batch_id)
            VALUES (?, ?, ?, NULL, ?, ?, 'USER', ?, 0, NULL)
            """.trimIndent(),
        ).use { st ->
            DEFAULT_CATEGORIES.forEachIndexed { index, cat ->
                st.reset()
                st.bindText(1, cat.id)
                st.bindText(2, cat.name)
                st.bindText(3, cat.kind.name)
                st.bindLong(4, cat.color.toLong())
                st.bindText(5, cat.icon)
                st.bindLong(6, index.toLong())
                st.step()
            }
        }
        SyncTriggers.create { connection.execSQL(it) }
    }
}
