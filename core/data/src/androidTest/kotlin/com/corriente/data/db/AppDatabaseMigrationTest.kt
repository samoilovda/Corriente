package com.corriente.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Каркас проверки миграций (ARCHITECTURE.md ADR-008, инвариант I-20). Пока есть только
 * версия 1 — здесь нечего мигрировать, тест проверяет, что схема, которую генерирует
 * [AppDatabase], совпадает с закоммиченным экспортом в core/data/schemas/1.json
 * (MigrationTestHelper сверяет это автоматически при createDatabase).
 *
 * Когда появится версия 2: скопировать структуру этого теста, добавить
 * `helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)`
 * и заполнить БД версии 1 тестовыми данными перед миграцией, чтобы проверить, что они
 * пережили переход (а не просто что schema-файл валиден).
 *
 * ВНИМАНИЕ: это инструментальный тест (src/androidTest) — требует подключённого устройства
 * или эмулятора. В среде без Android SDK (см. README "Известное ограничение окружения")
 * не запускался и не мог быть запущен; проверить в Android Studio.
 */
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun schemaAtVersion1MatchesExportedSchema() {
        // Бросит исключение, если реальная схема AppDatabase разошлась с core/data/schemas/1.json.
        helper.createDatabase(testDbName, 1).close()
    }

    // F1.5 — миграция v1 → v2 добавляет category.import_batch_id и не теряет данные.
    @Test
    fun migration1to2AddsImportBatchIdAndKeepsRows() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO category(id, name, kind, parent_id, color, origin, display_order, is_archived)
                VALUES ('c1', 'Еда', 'EXPENSE', NULL, 0, 'USER', 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT name, import_batch_id FROM category WHERE id = 'c1'").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("Еда", c.getString(0))
            assertNull(if (c.isNull(1)) null else c.getString(1)) // новая колонка = NULL у старой строки
        }
        db.close()
    }

    // R2.1 — миграция v2 → v3 создаёт `txn_fts`, наполняет её существующими операциями и держит
    // в синхроне через триггеры на будущих вставках/правках/удалениях.
    @Test
    fun migration2to3CreatesFtsTableAndBackfillsExistingNotes() {
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                """
                INSERT INTO currency(code, minor_units, display_scale, symbol, is_active, display_order)
                VALUES ('RUB', 2, 2, '₽', 1, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO account(id, name, currency_code, kind, opening_balance_minor, color, display_order, is_archived, include_in_total)
                VALUES ('a1', 'Наличные', 'RUB', 'CASH', 0, 0, 0, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO txn(id, kind, date, created_at, updated_at, account_id, amount_minor, currency_code, note)
                VALUES ('t1', 'EXPENSE', '2026-01-01', 0, 0, 'a1', 100, 'RUB', 'кофе с молоком')
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.MIGRATION_2_3)

        // Существующая до миграции заметка нашлась поиском (бэкфилл).
        db.query("SELECT docid FROM txn_fts WHERE txn_fts MATCH 'кофе*'").use { c ->
            assertEquals(true, c.moveToFirst())
        }

        // Новая операция синхронизируется в txn_fts автоматически (AFTER INSERT).
        db.execSQL(
            """
            INSERT INTO txn(id, kind, date, created_at, updated_at, account_id, amount_minor, currency_code, note)
            VALUES ('t2', 'EXPENSE', '2026-01-02', 0, 0, 'a1', 200, 'RUB', 'такси домой')
            """.trimIndent(),
        )
        db.query("SELECT docid FROM txn_fts WHERE txn_fts MATCH 'такси*'").use { c ->
            assertEquals(true, c.moveToFirst())
        }

        // Удаление операции убирает её из индекса (BEFORE DELETE).
        db.execSQL("DELETE FROM txn WHERE id = 't2'")
        db.query("SELECT docid FROM txn_fts WHERE txn_fts MATCH 'такси*'").use { c ->
            assertEquals(false, c.moveToFirst())
        }
        db.close()
    }

    // R2.3 — миграция v3 → v4 создаёт таблицу `budget` (бюджеты по категориям).
    @Test
    fun migration3to4CreatesBudgetTable() {
        helper.createDatabase(testDbName, 3).apply {
            execSQL(
                """
                INSERT INTO currency(code, minor_units, display_scale, symbol, is_active, display_order)
                VALUES ('RUB', 2, 2, '₽', 1, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO category(id, name, kind, parent_id, color, origin, display_order, is_archived)
                VALUES ('food', 'Еда', 'EXPENSE', NULL, 0, 'USER', 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 4, true, AppDatabase.MIGRATION_3_4)

        db.execSQL(
            """
            INSERT INTO budget(id, category_id, currency_code, amount_minor, period, starts_on)
            VALUES ('b1', 'food', 'RUB', 1000000, 'MONTH', '2026-09-01')
            """.trimIndent(),
        )
        db.query("SELECT category_id, amount_minor FROM budget WHERE id = 'b1'").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("food", c.getString(0))
            assertEquals(1000000, c.getLong(1))
        }

        // Бюджет «на всё» — category_id может быть NULL.
        db.execSQL(
            """
            INSERT INTO budget(id, category_id, currency_code, amount_minor, period, starts_on)
            VALUES ('b2', NULL, 'RUB', 5000000, 'MONTH', '2026-09-01')
            """.trimIndent(),
        )
        db.query("SELECT category_id FROM budget WHERE id = 'b2'").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(true, c.isNull(0))
        }
        db.close()
    }

}