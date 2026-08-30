package com.corriente.app.ui.report

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.corriente.app.R

/** Отчёт по категориям за период внутри одной валюты (ADR-012) — T1.8. */
@Composable
fun ReportScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_report)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nav_report))
        }
    }
}
