package com.corriente.app.quick

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.corriente.app.CorrienteApplication
import com.corriente.app.R
import com.corriente.app.applock.AppLockGate
import com.corriente.app.ui.theme.CorrienteTheme
import com.corriente.data.model.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * T4.4: смена активного счёта тапом по его названию в виджете. Полупрозрачный список
 * активных счетов; выбор пишется в [com.corriente.data.widget.WidgetConfigStore], после
 * чего WidgetUpdater пересчитывает снимок.
 *
 * R5.2: наследуется от [FragmentActivity] и оборачивает контент [AppLockGate] по той же
 * причине, что и [QuickExpenseActivity] — ещё одна Activity быстрого доступа из виджета,
 * открывающаяся поверх убитого процесса.
 */
class ChangeActiveAccountActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (applicationContext as CorrienteApplication).container

        setContent {
            CorrienteTheme {
                AppLockGate {
                    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
                    var busy by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        accounts = container.accountRepository.observeActive().first()
                    }

                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            tonalElevation = 6.dp,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(vertical = 12.dp).verticalScroll(rememberScrollState())) {
                                Text(
                                    stringResource(R.string.widget_active_account),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                                if (error) {
                                    Text(
                                        stringResource(R.string.quick_change_account_failed),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    )
                                }
                                accounts.forEach { account ->
                                    ListItem(
                                        headlineContent = { Text(account.name) },
                                        supportingContent = { Text(account.currency.code) },
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                            busy = true
                                            error = false
                                            // lifecycleScope: запись переживает пересоздание Activity (F0.6).
                                            lifecycleScope.launch {
                                                changeActiveAccount(container.widgetConfigStore, account.id)
                                                    .onSuccess { finish() }
                                                    .onFailure { busy = false; error = true }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
