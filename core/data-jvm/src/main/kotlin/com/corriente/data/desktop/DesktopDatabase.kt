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
 * База десктопа: тот же [AppDatabase] (схема 8), файл SQLite через встроенный драйвер. При создании файла — тот же засев,
 * что на Android (`seedCallback`): справочник ISO-4217 (активны RUB и USD), типовые категории с детерминированными id
 * (`seed-…`, I-27 — на телефоне те же, синхронизация сольёт их) и триггеры захвата изменений.
 *
 * Миграций у десктопа пока нет: он начинается со схемы 8. Следующая версия схемы обязана добавить миграцию и сюда.
 */
object DesktopDatabase {

    fun open(file: File): AppDatabase {
        file.parentFile?.mkdirs()
        return Room.databaseBuilder<AppDatabase>(name = file.absolutePath).configure()
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
