package com.corriente.app

import android.content.Context
import androidx.room.Room
import com.corriente.data.backup.AutoBackupSettings
import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.PreMigrationBackup
import com.corriente.data.imports.MonefyImportRepository
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.AccountBalanceUseCase
import com.corriente.data.usecase.CategoryReportUseCase

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
        // I-20: копия файла БД до того, как Room применит миграцию (схема ещё v1 — no-op).
        PreMigrationBackup.runIfNeeded(appContext, AppDatabase.DB_NAME, AppDatabase.SCHEMA_VERSION)
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addCallback(AppDatabase.seedCallback())
            .build()
    }

    val currencyRepository: CurrencyRepository by lazy { CurrencyRepository(database.currencyDao()) }
    val accountRepository: AccountRepository by lazy { AccountRepository(database.accountDao()) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val txnRepository: TxnRepository by lazy { TxnRepository(database.txnDao(), database.accountDao()) }
    val backupRepository: BackupRepository by lazy { BackupRepository(database) }
    val autoBackupSettings: AutoBackupSettings by lazy { AutoBackupSettings(database.appSettingDao()) }
    val monefyImportRepository: MonefyImportRepository by lazy { MonefyImportRepository(database) }
    val widgetConfigStore: WidgetConfigStore by lazy { WidgetConfigStore(appContext) }

    val accountBalanceUseCase: AccountBalanceUseCase by lazy {
        AccountBalanceUseCase(accountRepository, txnRepository)
    }
    val categoryReportUseCase: CategoryReportUseCase by lazy { CategoryReportUseCase(txnRepository) }
}
