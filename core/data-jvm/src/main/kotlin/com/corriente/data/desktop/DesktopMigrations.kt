package com.corriente.data.desktop

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

/**
 * Миграции базы десктопа (I-20). Android-миграции (`AppDatabaseAndroid.kt`) написаны на `SupportSQLiteDatabase` и в
 * десктопную сборку не входят; десктоп начинается со схемы 8, поэтому здесь — всё, что после неё. Каждая новая версия
 * схемы добавляет миграцию и в `AppDatabaseAndroid.kt`, и сюда (тест `DesktopMigrationTest`).
 */

/** 8 → 9: вид операции `ADJUSTMENT`; структура не меняется (см. `MIGRATION_8_9` на Android). */
private val DESKTOP_MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) = Unit
}

val DESKTOP_MIGRATIONS: Array<Migration> = arrayOf(DESKTOP_MIGRATION_8_9)
