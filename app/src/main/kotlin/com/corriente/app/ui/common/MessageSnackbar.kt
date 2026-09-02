package com.corriente.app.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Снекбар для [UiMessage] экрана (F0.2). Возвращает [SnackbarHostState] для `Scaffold`;
 * при появлении сообщения показывает его и зовёт [onConsumed].
 */
@Composable
fun rememberMessageSnackbarState(message: UiMessage?, onConsumed: () -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message.text)
            onConsumed()
        }
    }
    return host
}
