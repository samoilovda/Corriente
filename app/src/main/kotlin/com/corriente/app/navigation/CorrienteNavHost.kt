package com.corriente.app.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.corriente.app.ui.accounts.AccountsScreen
import com.corriente.app.ui.categories.CategoriesScreen
import com.corriente.app.ui.autobackup.AutoBackupScreen
import com.corriente.app.ui.currencies.CurrenciesScreen
import com.corriente.app.ui.fxreport.FxReportScreen
import com.corriente.app.ui.imports.ImportHistoryScreen
import com.corriente.app.ui.imports.ImportScreen
import com.corriente.app.ui.report.ReportScreen
import com.corriente.app.ui.settings.SettingsScreen
import com.corriente.app.ui.transactions.TransactionsScreen
import com.corriente.app.ui.transfer.TransferEntryScreen
import com.corriente.app.ui.widgetsettings.WidgetSettingsScreen
import com.corriente.app.ui.txnentry.EntryKind
import com.corriente.app.ui.txnentry.TxnEntryScreen

/** Маршруты вне нижней навигации (T1.2/T1.4 — из «Настроек»; T1.5 — ввод по FAB). */
private const val CURRENCIES_ROUTE = "currencies"
private const val CATEGORIES_ROUTE = "categories"
private const val IMPORT_ROUTE = "import_monefy"
private const val IMPORT_HISTORY_ROUTE = "import_history"
private const val WIDGET_SETTINGS_ROUTE = "widget_settings"
private const val AUTOBACKUP_ROUTE = "autobackup"
private const val FX_REPORT_ROUTE = "fx_report"
private const val TXN_ENTRY_ROUTE = "txn_entry"
private const val TXN_EDIT_ROUTE = "txn_edit"
private const val TRANSFER_ROUTE = "transfer"
private const val TRANSFER_EDIT_ROUTE = "transfer_edit"

/**
 * Каркас навигации (T1.1): нижняя панель с четырьмя разделами. Содержимое разделов
 * наполняется в T1.2–T1.9, каждый в своей задаче/коммите.
 */
@Composable
fun CorrienteNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            // Панель разделов — только на корневых экранах; под-экраны открываются поверх.
            if (CorrienteDestination.isTopLevelRoute(currentDestination?.route)) {
                NavigationBar {
                    CorrienteDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CorrienteDestination.TRANSACTIONS.route,
            // F2.4: корневой Scaffold отвечает за нижнюю панель; вложенные Scaffold'ы экранов
            // применяют оставшиеся вставки к своим TopAppBar — consumeWindowInsets убирает двойной учёт.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(CorrienteDestination.TRANSACTIONS.route) {
                TransactionsScreen(
                    onAddExpense = { navController.navigate("$TXN_ENTRY_ROUTE?kind=${EntryKind.EXPENSE.name}") },
                    onAddIncome = { navController.navigate("$TXN_ENTRY_ROUTE?kind=${EntryKind.INCOME.name}") },
                    onAddTransfer = { navController.navigate(TRANSFER_ROUTE) },
                    onEditTransaction = { id -> navController.navigate("$TXN_EDIT_ROUTE/$id") },
                    onEditTransfer = { id -> navController.navigate("$TRANSFER_EDIT_ROUTE/$id") },
                )
            }
            composable(CorrienteDestination.ACCOUNTS.route) { AccountsScreen() }
            composable(CorrienteDestination.REPORT.route) {
                ReportScreen(
                    onEditTransaction = { id -> navController.navigate("$TXN_EDIT_ROUTE/$id") },
                    onOpenFxReport = { navController.navigate(FX_REPORT_ROUTE) },
                )
            }
            composable(FX_REPORT_ROUTE) {
                FxReportScreen(onBack = { navController.popBackStack() })
            }
            composable(CorrienteDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenCurrencies = { navController.navigate(CURRENCIES_ROUTE) },
                    onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
                    onOpenImport = { navController.navigate(IMPORT_ROUTE) },
                    onOpenImportHistory = { navController.navigate(IMPORT_HISTORY_ROUTE) },
                    onOpenWidgetSettings = { navController.navigate(WIDGET_SETTINGS_ROUTE) },
                    onOpenAutoBackup = { navController.navigate(AUTOBACKUP_ROUTE) },
                )
            }
            composable(AUTOBACKUP_ROUTE) {
                AutoBackupScreen(onBack = { navController.popBackStack() })
            }
            composable(IMPORT_ROUTE) {
                ImportScreen(onBack = { navController.popBackStack() })
            }
            composable(IMPORT_HISTORY_ROUTE) {
                ImportHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(WIDGET_SETTINGS_ROUTE) {
                WidgetSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(CURRENCIES_ROUTE) {
                CurrenciesScreen(onBack = { navController.popBackStack() })
            }
            composable(CATEGORIES_ROUTE) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "$TXN_ENTRY_ROUTE?kind={kind}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType; defaultValue = EntryKind.EXPENSE.name },
                ),
            ) { entry ->
                TxnEntryScreen(
                    onDone = { navController.popBackStack() },
                    initialKind = EntryKind.valueOf(
                        entry.arguments?.getString("kind") ?: EntryKind.EXPENSE.name,
                    ),
                )
            }
            composable(
                "$TXN_EDIT_ROUTE/{txnId}",
                arguments = listOf(navArgument("txnId") { type = NavType.StringType }),
            ) { entry ->
                TxnEntryScreen(
                    onDone = { navController.popBackStack() },
                    editingTxnId = entry.arguments?.getString("txnId"),
                )
            }
            composable(TRANSFER_ROUTE) {
                TransferEntryScreen(onDone = { navController.popBackStack() })
            }
            composable(
                "$TRANSFER_EDIT_ROUTE/{txnId}",
                arguments = listOf(navArgument("txnId") { type = NavType.StringType }),
            ) { entry ->
                TransferEntryScreen(
                    onDone = { navController.popBackStack() },
                    editingTxnId = entry.arguments?.getString("txnId"),
                )
            }
        }
    }
}
