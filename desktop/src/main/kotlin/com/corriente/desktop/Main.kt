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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.system.exitProcess

private enum class Section(val title: String) { TRANSACTIONS("Операции"), ACCOUNTS("Счета"), SYNC("Синхронизация") }

private val DesktopLightColors = lightColorScheme(
    primary = Color(0xFF006780),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCE9FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF4C616B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6F2),
    onSecondaryContainer = Color(0xFF081E27),
    tertiary = Color(0xFF5B5B7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1DFFF),
    onTertiaryContainer = Color(0xFF181837),
    background = Color(0xFFF6FAFD),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF6FAFD),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE4E9),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C8CD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9EC),
    surfaceContainerHighest = Color(0xFFDFE3E7),
)

private val DesktopDarkColors = darkColorScheme(
    primary = Color(0xFF5DD5FC),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D62),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = Color(0xFFB3CAD6),
    onSecondary = Color(0xFF1E333C),
    secondaryContainer = Color(0xFF354A53),
    onSecondaryContainer = Color(0xFFCFE6F2),
    tertiary = Color(0xFFC3C2EA),
    onTertiary = Color(0xFF2C2C4D),
    tertiaryContainer = Color(0xFF434365),
    onTertiaryContainer = Color(0xFFE1DFFF),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFC0C8CD),
    outline = Color(0xFF8A9296),
    outlineVariant = Color(0xFF40484C),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF252B2E),
    surfaceContainerHighest = Color(0xFF303639),
)

private val DesktopShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

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
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) DesktopDarkColors else DesktopLightColors,
                shapes = DesktopShapes,
            ) {
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
