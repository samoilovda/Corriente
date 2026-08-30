package com.corriente.app.navigation

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.corriente.app.ui.accounts.AccountsScreen
import com.corriente.app.ui.report.ReportScreen
import com.corriente.app.ui.settings.SettingsScreen
import com.corriente.app.ui.transactions.TransactionsScreen

/**
 * Каркас навигации (T1.1): нижняя панель с четырьмя разделами, экраны пока placeholder —
 * настоящее содержимое появится в T1.2–T1.9, каждый в своей задаче/коммите.
 */
@Composable
fun CorrienteNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

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
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CorrienteDestination.TRANSACTIONS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(CorrienteDestination.TRANSACTIONS.route) { TransactionsScreen() }
            composable(CorrienteDestination.ACCOUNTS.route) { AccountsScreen() }
            composable(CorrienteDestination.REPORT.route) { ReportScreen() }
            composable(CorrienteDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
