package com.corriente.app.ui.transactions

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

/** Список операций с группировкой по дням, ввод расхода/дохода — T1.5/T1.6. */
@Composable
fun TransactionsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_transactions)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nav_transactions))
        }
    }
}
