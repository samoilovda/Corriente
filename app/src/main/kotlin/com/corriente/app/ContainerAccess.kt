package com.corriente.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Достаёт ручной DI-контейнер (ADR-011) из Application внутри Composable — точка,
 * через которую экраны получают репозитории для своих ViewModel-фабрик.
 */
@Composable
fun corrienteContainer(): AppContainer =
    (LocalContext.current.applicationContext as CorrienteApplication).container
