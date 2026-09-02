package com.corriente.app.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource

/**
 * Снекбар для [UiMessage] экрана (F0.2). Возвращает [SnackbarHostState] для `Scaffold`;
 * при появлении сообщения резолвит текст через [stringResource] (R6.3 — локаль устройства)
 * и показывает его, затем зовёт [onConsumed].
 */
@Composable
fun rememberMessageSnackbarState(message: UiMessage?, onConsumed: () -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val text = if (message != null) stringResource(message.resId, *message.args.toTypedArray()) else null
    LaunchedEffect(message) {
        if (text != null) {
            host.showSnackbar(text)
            onConsumed()
        }
    }
    return host
}
