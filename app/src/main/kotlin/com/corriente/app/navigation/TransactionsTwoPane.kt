package com.corriente.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.EmptyState
import com.corriente.app.ui.transactions.TransactionsScreen
import com.corriente.app.ui.transfer.TransferEntryScreen
import com.corriente.app.ui.transfer.TransferEntryViewModel
import com.corriente.app.ui.txnentry.EntryKind
import com.corriente.app.ui.txnentry.TxnEntryScreen
import com.corriente.app.ui.txnentry.TxnEntryViewModel

/** Что показано в правой панели. [token] меняется при каждом открытии — форма создаётся заново, а не переиспользуется. */
private sealed interface Detail {
    val token: Int

    data class Entry(
        override val token: Int,
        val kind: EntryKind = EntryKind.EXPENSE,
        val editingId: String? = null,
        val repeatId: String? = null,
        val categoryId: String? = null,
        val accountId: String? = null,
    ) : Detail

    data class Transfer(override val token: Int, val editingId: String? = null) : Detail
}

/**
 * UI-3.3: «Операции» на широком экране — слева список, справа ввод/правка выбранной операции
 * (list-detail вручную, без новых зависимостей). Закрытие формы возвращает правой панели подсказку.
 */
@Composable
fun TransactionsTwoPane(
    onCreateAccount: () -> Unit,
    onOpenBudgets: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
) {
    var detail by remember { mutableStateOf<Detail?>(null) }
    var counter by remember { mutableIntStateOf(0) }
    fun open(make: (Int) -> Detail) {
        counter += 1
        detail = make(counter)
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(420.dp).fillMaxHeight()) {
            TransactionsScreen(
                onAddExpense = { open { Detail.Entry(it, EntryKind.EXPENSE) } },
                onAddIncome = { open { Detail.Entry(it, EntryKind.INCOME) } },
                onAddTransfer = { open { Detail.Transfer(it) } },
                onEditTransaction = { id -> open { Detail.Entry(it, editingId = id) } },
                onEditTransfer = { id -> open { Detail.Transfer(it, editingId = id) } },
                onRepeatTransaction = { id, kind -> open { Detail.Entry(it, kind = kind, repeatId = id) } },
                onAddExpenseInCategory = { categoryId, accountId ->
                    open { Detail.Entry(it, EntryKind.EXPENSE, categoryId = categoryId, accountId = accountId) }
                },
                onOpenBudgets = onOpenBudgets,
                onOpenRecurring = onOpenRecurring,
                onCreateAccount = onCreateAccount,
                onOpenImport = onOpenImport,
                onOpenBackup = onOpenBackup,
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            when (val current = detail) {
                null -> EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = stringResource(R.string.txn_pane_empty_title),
                    body = stringResource(R.string.txn_pane_empty_body),
                )
                is Detail.Entry -> EntryPane(current, onDone = { detail = null }, onCreateAccount = onCreateAccount)
                is Detail.Transfer -> TransferPane(current, onDone = { detail = null })
            }
        }
    }
}

@Composable
private fun EntryPane(entry: Detail.Entry, onDone: () -> Unit, onCreateAccount: () -> Unit) {
    TxnEntryScreen(
        onDone = onDone,
        onCreateAccount = onCreateAccount,
        editingTxnId = entry.editingId,
        initialKind = entry.kind,
        initialCategoryId = entry.categoryId,
        initialAccountId = entry.accountId,
        repeatTxnId = entry.repeatId,
        // Свой ключ на каждое открытие — иначе ViewModel предыдущей формы переиспользуется в этой же панели.
        viewModel = viewModel(
            key = "pane-entry-${entry.token}",
            factory = with(corrienteContainer()) {
                TxnEntryViewModel.factory(
                    txnRepository, accountRepository, categoryRepository, currencyRepository,
                    entry.editingId, entry.kind, entry.categoryId, entry.accountId, entry.repeatId,
                )
            },
        ),
    )
}

@Composable
private fun TransferPane(transfer: Detail.Transfer, onDone: () -> Unit) {
    TransferEntryScreen(
        onDone = onDone,
        editingTxnId = transfer.editingId,
        viewModel = viewModel(
            key = "pane-transfer-${transfer.token}",
            factory = with(corrienteContainer()) {
                TransferEntryViewModel.factory(txnRepository, accountRepository, currencyRepository, transfer.editingId)
            },
        ),
    )
}
