package com.corriente.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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

/**
 * Схема Room (ARCHITECTURE.md §3.2; текущая версия — [SCHEMA_VERSION]). exportSchema=true — схемы коммитятся в `core/data/schemas/`
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
        com.corriente.data.sync.SyncStateEntity::class,
        com.corriente.data.sync.SyncOutboxEntity::class,
        com.corriente.data.sync.SyncRowMetaEntity::class,
        com.corriente.data.sync.CategoryRedirectEntity::class,
        com.corriente.data.sync.SyncDeferredEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
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
    abstract fun syncDao(): com.corriente.data.sync.SyncDao

    companion object {
        const val DB_NAME = "corriente.db"
        /** Единственный источник версии: аннотация [Database] и `BackupRepository` берут её отсюда. */
        const val SCHEMA_VERSION = 8
    }
}
