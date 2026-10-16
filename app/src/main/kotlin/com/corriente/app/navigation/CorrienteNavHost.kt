package com.corriente.app.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.corriente.app.ui.accountbalance.AccountBalanceScreen
import com.corriente.app.ui.accounts.AccountsScreen
import com.corriente.app.ui.applock.AppLockSettingsScreen
import com.corriente.app.ui.budgets.BudgetsScreen
import com.corriente.app.ui.categories.CategoriesScreen
import com.corriente.app.ui.autobackup.AutoBackupScreen
import com.corriente.app.ui.currencies.CurrenciesScreen
import com.corriente.app.ui.fxreport.FxReportScreen
import com.corriente.app.ui.imports.ImportHistoryScreen
import com.corriente.app.ui.imports.ImportScreen
import com.corriente.app.ui.recurring.RecurringScreen
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
private const val APP_LOCK_ROUTE = "app_lock"
private const val SYNC_ROUTE = "sync"
private const val BUDGETS_ROUTE = "budgets"
private const val RECURRING_ROUTE = "recurring"
private const val FX_REPORT_ROUTE = "fx_report"
private const val ACCOUNT_BALANCE_ROUTE = "account_balance"
private const val TXN_ENTRY_ROUTE = "txn_entry"
private const val TXN_EDIT_ROUTE = "txn_edit"
private const val TRANSFER_ROUTE = "transfer"
private const val TRANSFER_EDIT_ROUTE = "transfer_edit"

/** R4.4: схема deep link'ов для ярлыков приложения — не сетевой URI, чисто internal-routing. */
private const val DEEP_LINK_SCHEME = "corriente"

/**
 * Каркас навигации (T1.1): нижняя панель с четырьмя разделами. Содержимое разделов
 * наполняется в T1.2–T1.9, каждый в своей задаче/коммите.
 *
 * @param deepLinkIntent intent ярлыка приложения (R4.4) — с холодного старта [com.corriente.app.MainActivity.onCreate],
 *   при уже запущенной Activity — [com.corriente.app.MainActivity.onNewIntent]. `null`, если Activity
 *   открыта обычным способом (лаунчер/бэкстек).
 */
@Composable
fun CorrienteNavHost(deepLinkIntent: Intent? = null, onDeepLinkConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // R4.4: intent ярлыка обрабатывается через LaunchedEffect — handleDeepLink требует, чтобы
    // граф NavHost уже был построен, а эффекты в Compose выполняются уже после того, как вся
    // функция скомпозилась (в т.ч. NavHost ниже), независимо от места этого вызова в исходнике.
    LaunchedEffect(deepLinkIntent) {
        // R4.4: обрабатываем только intent'ы наших ярлыков (схема corriente://…). Обычный
        // запуск из лаунчера приходит сюда как ACTION_MAIN без data — его отдавать в
        // handleDeepLink нельзя: в navigation 2.9.0 это роняет NavController (getTopGraph NPE).
        val uri = deepLinkIntent?.data
        if (uri == null || uri.scheme != DEEP_LINK_SCHEME) return@LaunchedEffect
        val handled = runCatching { navController.handleDeepLink(deepLinkIntent) }.getOrDefault(false)
        if (handled == true) onDeepLinkConsumed()
    }

    val layout = navLayoutFor(LocalConfiguration.current.screenWidthDp)
    val topLevel = CorrienteDestination.isTopLevelRoute(currentDestination?.route)
    val navigateTo: (CorrienteDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // UI-3.3: две панели нужны только «Операциям»; остальным экранам на широком окне — читаемая ширина по центру,
    // а не растянутая телефонная вёрстка.
    val twoPane = layout == NavLayout.RAIL_TWO_PANE &&
        currentDestination?.route == CorrienteDestination.TRANSACTIONS.route

    Scaffold(
        bottomBar = {
            // Панель разделов — только на корневых экранах и только на телефоне; на широких окнах — рейка слева.
            if (layout == NavLayout.BOTTOM_BAR && topLevel) {
                NavigationBar {
                    CorrienteDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(destination) },
                            icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                            // T2: подпись в одну строку — иначе на узком экране с крупным шрифтом «Transactions»
                            // переносится по слогам. Если не помещается — уменьшаем шрифт (до 8sp), затем многоточие.
                            label = { NavLabel(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // F2.4: корневой Scaffold отвечает за нижнюю панель; вложенные Scaffold'ы экранов
        // применяют оставшиеся вставки к своим TopAppBar — consumeWindowInsets убирает двойной учёт.
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (layout != NavLayout.BOTTOM_BAR && topLevel) {
                NavigationRail {
                    CorrienteDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(destination) },
                            icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes), maxLines = 1) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        NavHost(
            navController = navController,
            startDestination = CorrienteDestination.TRANSACTIONS.route,
            modifier = if (twoPane) Modifier.fillMaxSize() else Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(),
        ) {
            composable(CorrienteDestination.TRANSACTIONS.route) {
                if (twoPane) {
                    TransactionsTwoPane(onCreateAccount = { navigateTo(CorrienteDestination.ACCOUNTS) })
                    return@composable
                }
                TransactionsScreen(
                    onAddExpense = { navController.navigate("$TXN_ENTRY_ROUTE?kind=${EntryKind.EXPENSE.name}") },
                    onAddIncome = { navController.navigate("$TXN_ENTRY_ROUTE?kind=${EntryKind.INCOME.name}") },
                    onAddTransfer = { navController.navigate(TRANSFER_ROUTE) },
                    onEditTransaction = { id -> navController.navigate("$TXN_EDIT_ROUTE/$id") },
                    onEditTransfer = { id -> navController.navigate("$TRANSFER_EDIT_ROUTE/$id") },
                    onRepeatTransaction = { id, kind -> navController.navigate("$TXN_ENTRY_ROUTE?kind=${kind.name}&repeat=$id") },
                    onAddExpenseInCategory = { categoryId, accountId ->
                        val account = accountId?.let { "&account=$it" }.orEmpty()
                        navController.navigate("$TXN_ENTRY_ROUTE?kind=${EntryKind.EXPENSE.name}&category=$categoryId$account")
                    },
                )
            }
            composable(CorrienteDestination.ACCOUNTS.route) {
                AccountsScreen(
                    onOpenAutoBackup = { navController.navigate(AUTOBACKUP_ROUTE) },
                    onOpenAccountBalance = { accountId -> navController.navigate("$ACCOUNT_BALANCE_ROUTE/$accountId") },
                )
            }
            composable(
                "$ACCOUNT_BALANCE_ROUTE/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
            ) { entry ->
                AccountBalanceScreen(
                    onBack = { navController.popBackStack() },
                    initialAccountId = entry.arguments?.getString("accountId"),
                )
            }
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
                    onOpenBudgets = { navController.navigate(BUDGETS_ROUTE) },
                    onOpenRecurring = { navController.navigate(RECURRING_ROUTE) },
                    onOpenAppLock = { navController.navigate(APP_LOCK_ROUTE) },
                    onOpenSync = { navController.navigate(SYNC_ROUTE) },
                )
            }
            composable(SYNC_ROUTE) {
                com.corriente.app.ui.sync.SyncScreen(onBack = { navController.popBackStack() })
            }
            composable(AUTOBACKUP_ROUTE) {
                AutoBackupScreen(onBack = { navController.popBackStack() })
            }
            composable(APP_LOCK_ROUTE) {
                AppLockSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(BUDGETS_ROUTE) {
                BudgetsScreen(onBack = { navController.popBackStack() })
            }
            composable(RECURRING_ROUTE) {
                RecurringScreen(onBack = { navController.popBackStack() })
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
                "$TXN_ENTRY_ROUTE?kind={kind}&category={category}&account={account}&repeat={repeat}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType; defaultValue = EntryKind.EXPENSE.name },
                    navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("repeat") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
                // R4.4: ярлыки «Записать расход»/«Записать доход» ведут сюда через deep link.
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME://entry?kind={kind}" }),
            ) { entry ->
                TxnEntryScreen(
                    onDone = { navController.popBackStack() },
                    onCreateAccount = {
                        // Сначала убираем экран ввода из стека, потом уходим на «Счета» тем же
                        // манёвром, что и нижняя навигация. Иначе экран ввода остаётся под
                        // «Счетами», и первый же тап по «Операциям» в нижней панели через
                        // saveState/restoreState воскрешает именно его (под-экран), а не корневой
                        // раздел — нижняя панель пропадает, и вкладка «Операции» становится
                        // недоступной до перезапуска приложения.
                        navController.popBackStack()
                        navController.navigate(CorrienteDestination.ACCOUNTS.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    // Неизвестный kind из deep link — расход, а не IllegalArgumentException.
                    initialKind = EntryKind.entries.firstOrNull { it.name == entry.arguments?.getString("kind") }
                        ?: EntryKind.EXPENSE,
                    initialCategoryId = entry.arguments?.getString("category"),
                    initialAccountId = entry.arguments?.getString("account"),
                    repeatTxnId = entry.arguments?.getString("repeat"),
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
            composable(
                TRANSFER_ROUTE,
                // R4.4: ярлык «Перевод» ведёт сюда через deep link.
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME://transfer" }),
            ) {
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
    }
}

/**
 * T2: подпись вкладки нижней навигации — одна строка без переноса по слогам. Если на узком экране с крупным
 * шрифтом не помещается, шрифт уменьшается ступенями (×0.9, но не меньше 8sp), и только потом — многоточие.
 */
@Composable
private fun NavLabel(text: String) {
    val baseSize = LocalTextStyle.current.fontSize
    var size by remember(text, baseSize) { mutableStateOf(baseSize) }
    Text(
        text = text,
        fontSize = size,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size.isSpecified && size > 8.sp) {
                val next = size * 9 / 10
                size = if (next > 8.sp) next else 8.sp
            }
        },
    )
}
