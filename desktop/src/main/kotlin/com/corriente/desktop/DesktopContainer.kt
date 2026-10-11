package com.corriente.desktop

import com.corriente.data.backup.BackupRepository
import com.corriente.data.category.OverviewCategoriesSettings
import com.corriente.data.desktop.DesktopDatabase
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.AccountBalanceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Зависимости десктопа — как `AppContainer` на Android (ручной DI, ADR-011): та же база и те же репозитории
 * из :core:data-jvm. Файл базы — в папке данных пользователя ([dataDir]).
 */
class DesktopContainer(val dataDir: File = defaultDataDir()) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = DesktopDatabase.open(File(dataDir, "corriente.db"))

    val currencies = CurrencyRepository(database.currencyDao())
    val accounts = AccountRepository(database.accountDao())
    val categories = CategoryRepository(database.categoryDao())
    val txns = TxnRepository(database.txnDao(), database.accountDao(), database.categoryDao())
    val balances = AccountBalanceUseCase(accounts, txns)
    val backup = BackupRepository(database)
    val overviewSettings = OverviewCategoriesSettings(database.appSettingDao())
    val sync = DesktopSync(database, scope) {
        val dir = File(dataDir, "backups").apply { mkdirs() }
        File(dir, "before-sync-join-${System.currentTimeMillis()}.json").outputStream().use { backup.export(it) }
    }

    companion object {
        /** macOS: ~/Library/Application Support/Corriente; Windows: %APPDATA%\Corriente; Linux: ~/.local/share/corriente. */
        fun defaultDataDir(): File {
            // Для проверки и разработки — отдельная папка, чтобы не трогать настоящие данные.
            System.getenv("CORRIENTE_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").lowercase()
            return when {
                "mac" in os -> File(home, "Library/Application Support/Corriente")
                "win" in os -> File(System.getenv("APPDATA") ?: home, "Corriente")
                else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "corriente")
            }
        }
    }
}
