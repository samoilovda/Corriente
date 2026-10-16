package com.corriente.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.corriente.app.R

/**
 * Четыре раздела нижней навигации (BUILD_PLAN.md T1.1): Операции/Счета/Отчёт/Настройки.
 * UI-1.4: невыбранный пункт — контурная иконка [icon], выбранный — залитая [selectedIcon].
 */
enum class CorrienteDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TRANSACTIONS("transactions", R.string.nav_transactions, Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
    ACCOUNTS("accounts", R.string.nav_accounts, Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    REPORT("report", R.string.nav_report, Icons.Outlined.PieChart, Icons.Filled.PieChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
    ;

    companion object {
        /**
         * true — [route] это один из четырёх корневых разделов. Под-экраны (валюты, категории,
         * импорт, ввод операции/перевода, FX-отчёт, настройки виджета и автобэкапа) открываются
         * поверх и нижнюю панель не показывают.
         */
        fun isTopLevelRoute(route: String?): Boolean = entries.any { it.route == route }
    }
}
