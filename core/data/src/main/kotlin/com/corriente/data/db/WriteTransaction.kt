package com.corriente.data.db

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Транзакция на запись через API соединений Room — одинаковый на Android и на десктопе (JVM). `withTransaction` из
 * Room — только Android, поэтому общий код (бэкап, импорт, синхронизация) пользуется этой функцией. Вызовы DAO внутри
 * [block] идут через то же соединение и входят в транзакцию; вложенный вызов становится точкой сохранения.
 */
suspend fun <R> RoomDatabase.writeTransaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
