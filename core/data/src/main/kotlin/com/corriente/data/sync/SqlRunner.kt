package com.corriente.data.sync

import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement

/**
 * Сырой SQL движка синхронизации через API соединений Room — одинаково на Android и на десктопе (JVM), в отличие от
 * `SupportSQLiteDatabase`/`Cursor`/`ContentValues`. Внутри транзакции ([com.corriente.data.db.writeTransaction])
 * пользуется тем же соединением.
 */
internal class SqlRunner(private val db: RoomDatabase) {

    suspend fun exec(sql: String, vararg args: Any?) {
        db.useWriterConnection { c -> c.usePrepared(sql) { st -> st.bindAll(args); st.step() } }
    }

    suspend fun <T> query(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): List<T> =
        db.useWriterConnection { c ->
            c.usePrepared(sql) { st ->
                st.bindAll(args)
                buildList { while (st.step()) add(row(st)) }
            }
        }

    suspend fun exists(sql: String, vararg args: Any?): Boolean = query(sql, *args) { true }.isNotEmpty()

    /** Сколько строк изменил последний UPDATE/INSERT/DELETE этого соединения. */
    suspend fun changes(): Long = query("SELECT changes()") { it.getLong(0) }.single()

    private fun SQLiteStatement.bindAll(args: Array<out Any?>) = args.forEachIndexed { i, value ->
        val index = i + 1
        when (value) {
            null -> bindNull(index)
            is String -> bindText(index, value)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            is Boolean -> bindLong(index, if (value) 1L else 0L)
            else -> bindText(index, value.toString())
        }
    }

    companion object {
        // Типы столбцов SQLite (sqlite3_column_type).
        const val TYPE_INTEGER = 1
        const val TYPE_FLOAT = 2
        const val TYPE_NULL = 5
    }
}
