package com.corriente.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corriente.data.usecase.totalsByCurrency

/** Счета и балансы — каждый в своей валюте; итог — по каждой валюте отдельно (I-8, ADR-012). */
@Composable
fun AccountsScreen(c: DesktopContainer) {
    val balances by c.balances.observeBalances().collectAsState(emptyList())
    val currencies by c.currencies.observeAll().collectAsState(emptyList())
    val byCode = currencies.associateBy { it.code.code }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Итого", style = MaterialTheme.typography.titleMedium)
        totalsByCurrency(balances).entries.sortedBy { it.key.code }.forEach { (_, money) ->
            Text(formatMoney(money, byCode), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 4.dp))
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Счета", style = MaterialTheme.typography.titleMedium)
        balances.sortedBy { it.account.displayOrder }.forEach { b ->
            ListItem(
                headlineContent = { Text(b.account.name) },
                supportingContent = { if (!b.account.includeInTotal) Text("не входит в итог") },
                trailingContent = {
                    Text(
                        formatMoney(b.balance, byCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (b.balance.amount.raw < 0) EXPENSE_COLOR else MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
            HorizontalDivider()
        }
        if (balances.isEmpty()) Text("Счетов пока нет — они придут с телефона после подключения синхронизации.")
    }
}
