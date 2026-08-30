package com.corriente.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.corriente.app.R

/** Четыре раздела нижней навигации (BUILD_PLAN.md T1.1): Операции/Счета/Отчёт/Настройки. */
enum class CorrienteDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    TRANSACTIONS("transactions", R.string.nav_transactions, Icons.Filled.List),
    ACCOUNTS("accounts", R.string.nav_accounts, Icons.Filled.AccountBalanceWallet),
    REPORT("report", R.string.nav_report, Icons.Filled.PieChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
