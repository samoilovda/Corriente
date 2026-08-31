package com.corriente.app.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.corriente.app.R

/** Счета и балансы по валютам (I-8: ни одного числа, суммирующего разные валюты) — T1.3/T1.7. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_accounts)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nav_accounts))
        }
    }
}
