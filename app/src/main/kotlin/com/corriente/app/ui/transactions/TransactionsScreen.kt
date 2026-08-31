package com.corriente.app.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.corriente.app.R

/** Список операций с группировкой по дням (T1.6); ввод расхода/дохода открывается по FAB (T1.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(onAddTransaction: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_transactions)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.txn_entry_title))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nav_transactions))
        }
    }
}
