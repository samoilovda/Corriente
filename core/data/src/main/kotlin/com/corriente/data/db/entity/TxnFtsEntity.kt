package com.corriente.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * R2.1: полнотекстовый индекс по заметке операции. `contentEntity = TxnEntity::class` —
 * "внешний контент" (Room/SQLite FTS4 external content table): строки самой `txn_fts` не
 * дублируют данные `txn`, а Room генерирует триггеры `AFTER INSERT/UPDATE`, `BEFORE UPDATE/DELETE`
 * на `txn`, которые держат виртуальную таблицу в синхроне автоматически — ни репозиторий,
 * ни DAO не обязаны сами писать в `txn_fts`.
 *
 * Схема v2 → v3 ([com.corriente.data.db.AppDatabase.MIGRATION_2_3]) создаёт эту таблицу и триггеры
 * вручную (то же самое, что Room сгенерировал бы при первой установке) и наполняет её из уже
 * существующих строк `txn` — иначе установленные до этой версии операции не находились бы поиском.
 */
@Fts4(contentEntity = TxnEntity::class)
@Entity(tableName = "txn_fts")
data class TxnFtsEntity(
    val note: String?,
)
