package com.corriente.data.sync

import com.corriente.data.backup.BackupRepository

/**
 * Захват изменений триггерами SQLite (docs/SYNC_DESIGN.md §2): любая запись в синхронизируемую таблицу — ввод,
 * импорт, слияние, откат батча, повторяющиеся платежи, виджет — кладёт `(таблица, ключ)` в `sync_outbox`.
 * Триггеры срабатывают только при `sync_state.enabled = 1 AND applying = 0`.
 *
 * В теле триггера нет `OR REPLACE`: правило конфликта внешнего запроса (Room пишет `UPDATE OR ABORT`, `INSERT OR ABORT`)
 * перекрывает правило внутри триггера, и повторная правка строки до выгрузки падала бы на ключе `sync_outbox`.
 * Поэтому вставка — только если записи ещё нет.
 *
 * Список таблиц меняется только вместе с миграцией схемы: триггеры создаются миграцией (и при создании файла БД),
 * а `IF NOT EXISTS` не пересоздаёт существующий триггер с другим телом.
 */
object SyncTriggers {

    /** Синхронизируемая таблица и SQL-выражение её ключа (для `NEW.`/`OLD.`). */
    data class Table(val name: String, val pkExpr: (row: String) -> String, val extraWhen: (row: String) -> String = { "1" })

    /** Разделитель частей составного ключа — управляющий символ, которого нет в данных. */
    const val PK_SEPARATOR = '\u001F'

    val TABLES: List<Table> = listOf(
        Table("currency", { "$it.code" }),
        Table("account", { "$it.id" }),
        Table("category", { "$it.id" }),
        Table("txn", { "$it.id" }),
        Table("budget", { "$it.id" }),
        Table("recurrence", { "$it.id" }),
        Table("import_batch", { "$it.id" }),
        Table("import_alias", { "$it.source_app || char(31) || $it.kind || char(31) || $it.source_value" }),
        Table("category_redirect", { "$it.from_id" }),
        // Привязанные к устройству настройки (замок, папка автобэкапа, sync.*) не уезжают на другие устройства.
        Table("app_setting", { "$it.key" }, { row ->
            BackupRepository.DEVICE_BOUND_PREFIXES.joinToString(" AND ") { "substr($row.key, 1, ${it.length}) <> '$it'" }
        }),
    )

    private const val ACTIVE = "(SELECT enabled = 1 AND applying = 0 FROM sync_state WHERE id = 1)"

    fun createStatements(): List<String> = TABLES.flatMap { t ->
        listOf(
            Triple("INSERT", "NEW", "AFTER INSERT"),
            Triple("UPDATE", "NEW", "AFTER UPDATE"),
            Triple("DELETE", "OLD", "AFTER DELETE"),
        ).map { (op, row, timing) ->
            """
            CREATE TRIGGER IF NOT EXISTS `sync_${t.name}_${op.lowercase()}` $timing ON `${t.name}`
            WHEN $ACTIVE AND (${t.extraWhen(row)})
            BEGIN
            INSERT INTO sync_outbox(table_name, pk) SELECT '${t.name}', ${t.pkExpr(row)}
            WHERE NOT EXISTS (SELECT 1 FROM sync_outbox WHERE table_name = '${t.name}' AND pk = ${t.pkExpr(row)});
            END
            """.trimIndent()
        }
    }

    /** [exec] — выполнить SQL: `SupportSQLiteDatabase::execSQL` в миграциях Android, соединение драйвера на десктопе. */
    fun create(exec: (String) -> Unit) = createStatements().forEach(exec)
}
