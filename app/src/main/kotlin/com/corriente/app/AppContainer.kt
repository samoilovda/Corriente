package com.corriente.app

import android.content.Context
import androidx.room.Room
import com.corriente.data.applock.AppLockSettings
import com.corriente.data.backup.AutoBackupSettings
import com.corriente.data.category.OverviewCategoriesSettings
import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.AppDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.corriente.data.db.PreMigrationBackup
import com.corriente.data.db.PreMigrationSnapshotFactory
import com.corriente.data.imports.MonefyImportRepository
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.data.recurrence.RecurrenceErrors
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.BudgetRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.RecurrenceRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.AccountBalanceUseCase

/**
 * Ручной DI-контейнер (ADR-011, BUILD_PLAN.md §5.3): для полутора десятков зависимостей
 * Hilt/KSP-кодогенерация не окупается, а её ошибки ("cannot find symbol" в сгенерированном
 * файле) — худший вид обратной связи именно при разработке через агента, когда причина
 * ошибки физически не в том файле, который видно.
 *
 * Один инстанс на весь процесс, живёт в [CorrienteApplication]. Никакого сетевого клиента
 * здесь нет и не будет (I-24).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = run {
        // A2.15: восстановление копии «до миграции» отложено до перезапуска — файл БД подменяем до её открытия.
        PreMigrationBackup.applyPendingRestore(appContext, AppDatabase.DB_NAME)
        // I-20: копия файла БД до того, как Room применит миграцию. A2.15: не в Application.onCreate на главном
        // потоке, а перед первым открытием файла — на потоке первого запроса Room.
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(
                PreMigrationSnapshotFactory(FrameworkSQLiteOpenHelperFactory()) {
                    PreMigrationBackup.runIfNeeded(appContext, AppDatabase.DB_NAME, AppDatabase.SCHEMA_VERSION)
                },
            )
            .addCallback(AppDatabase.seedCallback { id -> defaultCategoryNameRes[id]?.let(appContext::getString) })
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    val currencyRepository: CurrencyRepository by lazy { CurrencyRepository(database.currencyDao()) }
    val accountRepository: AccountRepository by lazy { AccountRepository(database.accountDao()) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val txnRepository: TxnRepository by lazy { TxnRepository(database.txnDao(), database.accountDao(), database.categoryDao()) }
    val backupRepository: BackupRepository by lazy { BackupRepository(database) }
    val autoBackupSettings: AutoBackupSettings by lazy { AutoBackupSettings(database.appSettingDao()) }
    /** R5.2: настройка блокировки приложения (не шифрование — см. AppLockScreen.kt). */
    val appLockSettings: AppLockSettings by lazy { AppLockSettings(database.appSettingDao()) }
    /** Какие категории стоят вокруг кольца на главном. */
    val overviewCategoriesSettings: OverviewCategoriesSettings by lazy { OverviewCategoriesSettings(database.appSettingDao()) }
    val monefyImportRepository: MonefyImportRepository by lazy { MonefyImportRepository(database) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database.budgetDao()) }
    val recurrenceRepository: RecurrenceRepository by lazy { RecurrenceRepository(database.recurrenceDao(), database.txnDao()) }
    val recurrenceErrors: RecurrenceErrors by lazy { RecurrenceErrors(database.appSettingDao()) }
    val widgetConfigStore: WidgetConfigStore by lazy { WidgetConfigStore(appContext) }

    val accountBalanceUseCase: AccountBalanceUseCase by lazy {
        AccountBalanceUseCase(accountRepository, txnRepository)
    }

    /**
     * Копия текущей БД в `databases/pre-migration/` перед восстановлением из бэкапа (F1.4). A2.15: WAL сначала
     * сбрасывается в основной файл — снимок не зависит от «живого» `-wal`.
     */
    suspend fun snapshotDatabaseBeforeRestore() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
            PreMigrationBackup.copyDatabaseFile(appContext, AppDatabase.DB_NAME, "pre-restore")
        }
    }
}
