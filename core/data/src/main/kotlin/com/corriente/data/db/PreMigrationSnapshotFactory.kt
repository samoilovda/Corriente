package com.corriente.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * A2.15: выполняет [beforeFirstOpen] (копия БД «до миграции», [PreMigrationBackup.runIfNeeded]) перед самым первым
 * открытием файла БД — но на том потоке, который Room использует для запроса, а не в `Application.onCreate` на
 * главном потоке (открытие SQLite и копирование файла на большой БД — риск ANR при первом запуске новой версии).
 * Room открывает БД лениво, при первом запросе (DAO — `suspend`/`Flow`, главный поток запрещён), а миграция идёт
 * уже внутри этого открытия, то есть после [beforeFirstOpen].
 */
class PreMigrationSnapshotFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val beforeFirstOpen: () -> Unit,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val helper = delegate.create(configuration)
        return object : SupportSQLiteOpenHelper by helper {
            private var done = false

            private fun ensureSnapshot() {
                if (done) return
                synchronized(this) {
                    if (!done) {
                        beforeFirstOpen()
                        done = true
                    }
                }
            }

            override val writableDatabase: SupportSQLiteDatabase
                get() {
                    ensureSnapshot()
                    return helper.writableDatabase
                }

            override val readableDatabase: SupportSQLiteDatabase
                get() {
                    ensureSnapshot()
                    return helper.readableDatabase
                }
        }
    }
}
