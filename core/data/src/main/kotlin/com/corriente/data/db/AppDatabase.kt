package com.corriente.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.dao.BudgetDao
import com.corriente.data.db.dao.CategoryDao
import com.corriente.data.db.dao.CurrencyDao
import com.corriente.data.db.dao.ImportAliasDao
import com.corriente.data.db.dao.ImportBatchDao
import com.corriente.data.db.dao.RecurrenceDao
import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.db.entity.BudgetEntity
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.ImportAliasEntity
import com.corriente.data.db.entity.ImportBatchEntity
import com.corriente.data.db.entity.RecurrenceEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnFtsEntity
import com.corriente.data.seed.DEFAULT_CATEGORIES
import com.corriente.data.seed.ISO_CURRENCIES

/**
 * Схема v1 (ARCHITECTURE.md §3.2). exportSchema=true — схемы коммитятся в `core/data/schemas/`
 * (ADR-008, I-20): каждая следующая версия обязана иметь явный [androidx.room.migration.Migration]
 * с тестом через `MigrationTestHelper`, `fallbackToDestructiveMigration` запрещён навсегда.
 */
@Database(
    entities = [
        CurrencyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TxnEntity::class,
        ImportBatchEntity::class,
        ImportAliasEntity::class,
        AppSettingEntity::class,
        TxnFtsEntity::class,
        BudgetEntity::class,
        RecurrenceEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun importAliasDao(): ImportAliasDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurrenceDao(): RecurrenceDao

    companion object {
        const val DB_NAME = "corriente.db"

        /** Держать в синхроне с `version` в аннотации [Database]. */
        const val SCHEMA_VERSION = 6

        /**
         * v1 → v2 (F1.5): `category.import_batch_id` — чтобы откат импорта удалял только свои
         * осиротевшие IMPORT-категории. Nullable-колонка, старые строки получают NULL.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE category ADD COLUMN import_batch_id TEXT")
            }
        }

        /**
         * v2 → v3 (R2.1): полнотекстовый поиск по заметке. Виртуальная таблица `txn_fts`
         * (FTS4, "external content" — content=`txn`) плюс четыре триггера синхронизации —
         * ровно то, что Room сгенерировал бы сам при первой установке для
         * `@Fts4(contentEntity = TxnEntity::class)` ([TxnFtsEntity]); здесь пишем руками, потому
         * что миграция не может положиться на `onCreate`. `INSERT ... SELECT rowid, note FROM txn`
         * наполняет индекс из уже существующих операций — иначе введённые до этой версии заметки
         * не находились бы поиском, пока их не отредактируют.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `txn_fts` USING FTS4(`note`, content=`txn`)")
                db.execSQL("INSERT INTO `txn_fts`(`docid`, `note`) SELECT `rowid`, `note` FROM `txn`")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_txn_fts_BEFORE_UPDATE` BEFORE UPDATE ON `txn` BEGIN
                    DELETE FROM `txn_fts` WHERE `docid`=OLD.`rowid`;
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_txn_fts_BEFORE_DELETE` BEFORE DELETE ON `txn` BEGIN
                    DELETE FROM `txn_fts` WHERE `docid`=OLD.`rowid`;
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_txn_fts_AFTER_UPDATE` AFTER UPDATE ON `txn` BEGIN
                    INSERT INTO `txn_fts`(`docid`, `note`) VALUES (NEW.`rowid`, NEW.`note`);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_txn_fts_AFTER_INSERT` AFTER INSERT ON `txn` BEGIN
                    INSERT INTO `txn_fts`(`docid`, `note`) VALUES (NEW.`rowid`, NEW.`note`);
                    END
                    """.trimIndent(),
                )
            }
        }

        /**
         * v3 → v4 (R2.3): таблица `budget` — бюджет по категории (или «на всё», `category_id`
         * NULL), строго в одной валюте (ADR-012/I-8, `currency_code` NOT NULL). `period` пока
         * только `MONTH`, хранится строкой (I-14-стиль: значения enum'а — данные Room, не число).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget` (
                        `id` TEXT NOT NULL,
                        `category_id` TEXT,
                        `currency_code` TEXT NOT NULL,
                        `amount_minor` INTEGER NOT NULL,
                        `period` TEXT NOT NULL,
                        `starts_on` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`currency_code`) REFERENCES `currency`(`code`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_category_id` ON `budget` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_currency_code` ON `budget` (`currency_code`)")
            }
        }

        /**
         * v4 → v5 (R2.4): таблица `recurrence` — шаблон повторяющейся операции (расход/доход,
         * перевод не поддерживается) плюс правило повторения и `next_run_on`/
         * `last_created_txn_id` для идемпотентного воркера.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurrence` (
                        `id` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `category_id` TEXT,
                        `amount_minor` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `note` TEXT,
                        `rule_type` TEXT NOT NULL,
                        `day_of_month` INTEGER,
                        `interval_days` INTEGER,
                        `next_run_on` TEXT NOT NULL,
                        `last_created_txn_id` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`account_id`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`currency_code`) REFERENCES `currency`(`code`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurrence_account_id` ON `recurrence` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurrence_category_id` ON `recurrence` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurrence_currency_code` ON `recurrence` (`currency_code`)")
            }
        }

        /**
         * v5 → v6 (доработка после R6): базовые типовые категории «из коробки». Структура схемы
         * не меняется — только данные: вставляем [DEFAULT_CATEGORIES], но лишь если таблица
         * `category` пуста, чтобы не спорить с уже заведёнными категориями и не упереться в
         * уникальный индекс (name, kind). Тот же набор при первой установке кладёт [seedCallback].
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedDefaultCategoriesIfEmpty(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        /**
         * Вставляет [DEFAULT_CATEGORIES] сырым SQL — только когда в `category` нет ни одной строки.
         * Общий код для [seedCallback] (первая установка) и [MIGRATION_5_6] (уже установленные
         * копии). `origin = 'USER'`, `import_batch_id = NULL` — обычные пользовательские категории.
         */
        private fun seedDefaultCategoriesIfEmpty(db: SupportSQLiteDatabase) {
            db.query("SELECT COUNT(*) FROM category").use { c ->
                if (!c.moveToFirst() || c.getInt(0) != 0) return
            }
            db.beginTransaction()
            try {
                DEFAULT_CATEGORIES.forEachIndexed { index, cat ->
                    db.execSQL(
                        """
                        INSERT INTO category(id, name, kind, parent_id, color, icon, origin, display_order, is_archived, import_batch_id)
                        VALUES (?, ?, ?, NULL, ?, ?, 'USER', ?, 0, NULL)
                        """.trimIndent(),
                        arrayOf<Any>(cat.id, cat.name, cat.kind.name, cat.color, cat.icon, index),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        /**
         * Сеет полный справочник ISO-4217 при создании файла БД (I-14). Выполняется как сырой
         * SQL внутри `onCreate`, а не через сгенерированный DAO: в момент вызова колбэка Room
         * ещё не гарантирует готовность DAO-инстанса того же соединения, а `execSQL` — гарантированно
         * синхронный и однозначный путь.
         *
         * По умолчанию активны только RUB и USD (текущая базовая валюта пользователя и её будущий
         * преемник, ARCHITECTURE.md §7 вопрос 3) — остальные пользователь включает сам в T1.2.
         */
        fun seedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.beginTransaction()
                try {
                    ISO_CURRENCIES.forEach { c ->
                        val isActive = if (c.code == "RUB" || c.code == "USD") 1 else 0
                        db.execSQL(
                            """
                            INSERT INTO currency(code, minor_units, display_scale, symbol, is_active, display_order)
                            VALUES (?, ?, ?, ?, ?, 0)
                            """.trimIndent(),
                            arrayOf<Any>(c.code, c.minorUnits, c.minorUnits, c.symbol, isActive),
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                // Типовые категории «из коробки» — тот же набор, что кладёт миграция v5→v6
                // для уже установленных копий (своя транзакция внутри).
                seedDefaultCategoriesIfEmpty(db)
            }
        }
    }
}
