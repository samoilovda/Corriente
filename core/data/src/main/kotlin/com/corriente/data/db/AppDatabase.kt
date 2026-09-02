package com.corriente.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.dao.CategoryDao
import com.corriente.data.db.dao.CurrencyDao
import com.corriente.data.db.dao.ImportAliasDao
import com.corriente.data.db.dao.ImportBatchDao
import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AppSettingEntity
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.ImportAliasEntity
import com.corriente.data.db.entity.ImportBatchEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnFtsEntity
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
    ],
    version = 3,
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

    companion object {
        const val DB_NAME = "corriente.db"

        /** Держать в синхроне с `version` в аннотации [Database]. */
        const val SCHEMA_VERSION = 3

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

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
            }
        }
    }
}
