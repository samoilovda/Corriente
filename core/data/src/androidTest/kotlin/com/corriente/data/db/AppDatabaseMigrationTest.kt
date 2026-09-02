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
}
