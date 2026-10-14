package com.corriente.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.system.exitProcess

private enum class Section(val title: String) { TRANSACTIONS("Операции"), ACCOUNTS("Счета"), SYNC("Синхронизация") }

fun main() {
    // Имя в меню macOS при запуске из Gradle (в собранном .dmg оно уже «Corriente»); до создания окна.
    System.setProperty("apple.awt.application.name", "Corriente")
    val container = DesktopContainer()
    container.sync.start()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Corriente",
            state = rememberWindowState(size = DpSize(1180.dp, 820.dp)),
        ) {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(container) }
            }
        }
    }
    // Фоновый обмен идёт в своих потоках — закрытие окна завершает процесс сразу.
    exitProcess(0)
}

@Composable
internal fun App(c: DesktopContainer) {
    var section by remember { mutableStateOf(Section.TRANSACTIONS) }
    var entry by remember { mutableStateOf<EntryRequest?>(null) }
    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Section.entries.forEach { s ->
                NavigationRailItem(
                    selected = section == s,
                    onClick = { section = s },
                    icon = {
                        Icon(
                            when (s) {
                                Section.TRANSACTIONS -> Icons.AutoMirrored.Filled.List
                                Section.ACCOUNTS -> Icons.Filled.AccountBalanceWallet
                                Section.SYNC -> Icons.Filled.Sync
                            },
                            contentDescription = null,
                        )
                    },
                    label = { Text(s.title) },
                )
            }
        }
        when (section) {
            Section.TRANSACTIONS -> TransactionsScreen(c) { entry = it }
            Section.ACCOUNTS -> AccountsScreen(c)
            Section.SYNC -> SyncScreen(c)
        }
    }
    entry?.let { request -> EntryDialog(c, request) { entry = null } }
}
