package com.corriente.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
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

// Android-часть AppDatabase: миграции, засев и колбэки на SupportSQLiteDatabase. Вынесено из AppDatabase.kt, чтобы сам
// класс базы компилировался и для десктопа (модуль :core:data-jvm исключает этот файл). Обращения остаются прежними —
// `AppDatabase.MIGRATION_7_8`, `AppDatabase.seedCallback()` — через расширения companion-объекта.


/**
 * v1 → v2 (F1.5): `category.import_batch_id` — чтобы откат импорта удалял только свои
 * осиротевшие IMPORT-категории. Nullable-колонка, старые строки получают NULL.
 */
private val MIGRATION_1_2_DEF = object : Migration(1, 2) {
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
private val MIGRATION_2_3_DEF = object : Migration(2, 3) {
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
private val MIGRATION_3_4_DEF = object : Migration(3, 4) {
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
private val MIGRATION_4_5_DEF = object : Migration(4, 5) {
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
private val MIGRATION_5_6_DEF = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        seedDefaultCategoriesIfEmpty(db)
    }
}

/**
 * v6 → v7 (A1.10): `txn_fts` с токенайзером `unicode61` — поиск по заметке нечувствителен к регистру
 * кириллицы. Триггеры и таблица пересоздаются, индекс перестраивается из `txn` командой `rebuild`
 * (заметки, введённые до миграции, находятся).
 */
private val MIGRATION_6_7_DEF = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        FTS_TRIGGER_SUFFIXES.forEach { db.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_txn_fts_$it`") }
        db.execSQL("DROP TABLE IF EXISTS `txn_fts`")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `txn_fts` USING FTS4(`note`, tokenize=unicode61, content=`txn`)")
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
        db.execSQL("INSERT INTO `txn_fts`(`txn_fts`) VALUES('rebuild')")
    }
}

/**
 * v7 → v8 (синхронизация, docs/SYNC_DESIGN.md S1): служебные таблицы `sync_state`, `sync_outbox`, `sync_row_meta`,
 * `category_redirect`, `sync_deferred` и триггеры захвата изменений ([com.corriente.data.sync.SyncTriggers]). Данные не меняются;
 * пока синхронизация не включена (строки в `sync_state` нет), триггеры ничего не пишут.
 */
private val MIGRATION_7_8_DEF = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SYNC_TABLES_SQL.forEach(db::execSQL)
        com.corriente.data.sync.SyncTriggers.create(db::execSQL)
    }
}

/** DDL служебных таблиц схемы 8 — ровно то, что генерирует Room (см. `schemas/.../8.json`). */
private val SYNC_TABLES_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `applying` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `sync_outbox` (`table_name` TEXT NOT NULL, `pk` TEXT NOT NULL, PRIMARY KEY(`table_name`, `pk`))",
    "CREATE TABLE IF NOT EXISTS `sync_row_meta` (`table_name` TEXT NOT NULL, `pk` TEXT NOT NULL, `hlc` TEXT NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`table_name`, `pk`))",
    "CREATE TABLE IF NOT EXISTS `category_redirect` (`from_id` TEXT NOT NULL, `into_id` TEXT NOT NULL, PRIMARY KEY(`from_id`))",
    "CREATE TABLE IF NOT EXISTS `sync_deferred` (`table_name` TEXT NOT NULL, `pk` TEXT NOT NULL, `hlc` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`table_name`, `pk`))",
)

/**
 * Триггеры захвата изменений для БД, созданных не через [seedCallback] (тесты синхронизации на БД в памяти).
 * В приложении их ставят [seedCallback] (новый файл) и [MIGRATION_7_8_DEF] (обновление).
 */
private val syncTriggersCallback_DEF: Callback = object : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        com.corriente.data.sync.SyncTriggers.create(db::execSQL)
    }
}

private val FTS_TRIGGER_SUFFIXES = listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")

private val ALL_MIGRATIONS_DEF: Array<Migration> = arrayOf(
    MIGRATION_1_2_DEF, MIGRATION_2_3_DEF, MIGRATION_3_4_DEF, MIGRATION_4_5_DEF, MIGRATION_5_6_DEF, MIGRATION_6_7_DEF, MIGRATION_7_8_DEF,
)

/**
 * Вставляет [DEFAULT_CATEGORIES] сырым SQL — только когда в `category` нет ни одной строки.
 * Общий код для [seedCallback] (первая установка) и [MIGRATION_5_6_DEF] (уже установленные
 * копии). `origin = 'USER'`, `import_batch_id = NULL` — обычные пользовательские категории.
 */
private fun seedDefaultCategoriesIfEmpty(db: SupportSQLiteDatabase, nameOf: (String) -> String? = { null }) {
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
                arrayOf<Any>(cat.id, nameOf(cat.id) ?: cat.name, cat.kind.name, cat.color, cat.icon, index),
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
/** [categoryName] — локализованное имя типовой категории по её id (A2.13); `null` — русское имя из [DEFAULT_CATEGORIES]. */
fun AppDatabase.Companion.seedCallback(categoryName: (String) -> String? = { null }): Callback = object : Callback() {
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
        seedDefaultCategoriesIfEmpty(db, categoryName)
        // Синхронизация (схема 8): триггеры захвата — как их создаёт MIGRATION_7_8_DEF у обновлённых копий.
        com.corriente.data.sync.SyncTriggers.create(db::execSQL)
    }
}

val AppDatabase.Companion.MIGRATION_1_2: Migration get() = MIGRATION_1_2_DEF
val AppDatabase.Companion.MIGRATION_2_3: Migration get() = MIGRATION_2_3_DEF
val AppDatabase.Companion.MIGRATION_3_4: Migration get() = MIGRATION_3_4_DEF
val AppDatabase.Companion.MIGRATION_4_5: Migration get() = MIGRATION_4_5_DEF
val AppDatabase.Companion.MIGRATION_5_6: Migration get() = MIGRATION_5_6_DEF
val AppDatabase.Companion.MIGRATION_6_7: Migration get() = MIGRATION_6_7_DEF
val AppDatabase.Companion.MIGRATION_7_8: Migration get() = MIGRATION_7_8_DEF
val AppDatabase.Companion.syncTriggersCallback: Callback get() = syncTriggersCallback_DEF
val AppDatabase.Companion.ALL_MIGRATIONS: Array<Migration> get() = ALL_MIGRATIONS_DEF
