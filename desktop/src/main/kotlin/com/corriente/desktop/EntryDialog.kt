package com.corriente.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Txn
import com.corriente.money.AmountInput
import com.corriente.money.Money
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Ввод и правка расхода/дохода. Сумма — с клавиатуры (Enter — сохранить), категория — сеткой, как на телефоне.
 * Запись — через [com.corriente.data.repository.TxnRepository]: те же проверки инвариантов (валюта операции = валюта
 * счёта, вид категории и т. п.), что на Android.
 */
@Composable
fun EntryDialog(c: DesktopContainer, request: EntryRequest, onDone: () -> Unit) {
    val editing = (request as? EntryRequest.Edit)?.txn
    val accounts by c.accounts.observeActive().collectAsState(emptyList())
    val categories by c.categories.observeActive().collectAsState(emptyList())
    val currencies by c.currencies.observeAll().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var income by remember { mutableStateOf(editing is Txn.Income || (request as? EntryRequest.New)?.income == true) }
    var accountId by remember {
        mutableStateOf(
            when (editing) {
                is Txn.Expense -> editing.accountId
                is Txn.Income -> editing.accountId
                else -> (request as? EntryRequest.New)?.accountId
            },
        )
    }
    var categoryId by remember {
        mutableStateOf(
            when (editing) {
                is Txn.Expense -> editing.categoryId
                is Txn.Income -> editing.categoryId
                else -> (request as? EntryRequest.New)?.categoryId
            },
        )
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf((editing?.date ?: LocalDate.now()).toString()) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Счёт по умолчанию — первый активный; сумма при правке — из операции в валюте её счёта.
    LaunchedEffect(accounts, currencies) {
        if (accountId == null) accountId = accounts.firstOrNull()?.id
        if (editing != null && amountText.isEmpty()) {
            val money = when (editing) {
                is Txn.Expense -> editing.amount
                is Txn.Income -> editing.amount
                is Txn.Transfer -> null
            }
            val currency = money?.let { currencyOf(it.currency, currencies.associateBy { c -> c.code.code }) }
            if (money != null && currency != null) amountText = AmountInput.fromMinor(money.amount, currency).displayText()
        }
    }

    val account = accounts.firstOrNull { it.id == accountId }
    val currency = account?.let { currencyOf(it.currency, currencies.associateBy { c -> c.code.code }) }
    val kind = if (income) CategoryKind.INCOME else CategoryKind.EXPENSE
    val choices = categories.filter { it.kind == kind }

    fun save() {
        val cur = currency ?: return run { error = "Выберите счёт" }
        val minor = AmountInput.fromText(amountText.replace(',', '.').replace(" ", ""), cur).toMinorOrNull(cur)
        if (minor == null || minor.raw <= 0) return run { error = "Введите сумму больше нуля" }
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: return run { error = "Дата в формате ГГГГ-ММ-ДД" }
        val money = Money(minor, cur.code)
        val cat = categoryId?.takeIf { id -> choices.any { it.id == id } }
        val text = note.trim().ifBlank { null }
        scope.launch {
            runCatching {
                when {
                    editing != null -> c.txns.updateEntry(editing.id, account.id, money, cat, date, text)
                    income -> c.txns.addIncome(account.id, money, cat, date, text)
                    else -> c.txns.addExpense(account.id, money, cat, date, text)
                }
            }.onSuccess { onDone() }.onFailure { error = it.message ?: "Не удалось сохранить" }
        }
    }

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (editing != null) "Операция" else if (income) "Новый доход" else "Новый расход") },
        text = {
            Column(
                Modifier.width(560.dp).onPreviewKeyEvent {
                    // Enter сохраняет из любого поля и после выбора категории мышью (фокус уходит с суммы).
                    // Только нажатие: на отпускание save() второй раз записал бы дубль операции.
                    if (it.key == Key.Enter) { if (it.type == KeyEventType.KeyDown) save(); true } else false
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !income, onClick = { income = false; categoryId = null }, label = { Text("Расход") })
                        FilterChip(selected = income, onClick = { income = true; categoryId = null }, label = { Text("Доход") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' || it == ',' }; error = null },
                        label = { Text("Сумма${currency?.let { ", ${it.code.code}" }.orEmpty()}") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.End),
                        modifier = Modifier.weight(1f).testTag("amount").focusRequester(amountFocus),
                    )
                    AccountPicker(accounts.map { it.id to "${it.name} · ${it.currency.code}" }, account?.let { "${it.name} · ${it.currency.code}" } ?: "Счёт") {
                        accountId = it
                        // Валюта меняется вместе со счётом — вводимая сумма пересобирается под точность новой валюты.
                        val newCurrency = accounts.firstOrNull { a -> a.id == it }
                            ?.let { a -> currencyOf(a.currency, currencies.associateBy { c -> c.code.code }) }
                        if (newCurrency != null) amountText = AmountInput.fromText(amountText, newCurrency).displayText()
                    }
                }
                Text("Категория", style = MaterialTheme.typography.labelMedium)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    items(choices, key = { it.id }) { cat ->
                        val selected = cat.id == categoryId
                        Column(
                            Modifier
                                .testTag("cat-${cat.id}")
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { categoryId = if (selected) null else cat.id }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CategoryBadge(cat.icon, cat.name, cat.color)
                            Text(cat.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(dateText, { dateText = it; error = null }, label = { Text("Дата") }, singleLine = true, modifier = Modifier.width(160.dp).testTag("date"))
                    OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = {
                        scope.launch { runCatching { c.txns.deleteById(editing.id) }.onSuccess { onDone() }.onFailure { error = it.message } }
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDone) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun AccountPicker(options: List<Pair<String, String>>, label: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("account-picker")) {
            Text(label)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(id) }) }
        }
    }
}
