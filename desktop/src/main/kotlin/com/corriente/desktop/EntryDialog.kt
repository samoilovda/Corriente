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
import com.corriente.money.AmountParse
import com.corriente.money.AmountParseError
import com.corriente.money.StrictAmountParser
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
    // U13: запись в полёте — второй Enter/клик до закрытия окна не создаёт дубль.
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // U13: исходные значения формы — Esc на изменённой форме спрашивает, а не теряет ввод молча.
    var initialAmountText by remember { mutableStateOf("") }
    val initialIncome = remember { income }
    val initialCategoryId = remember { categoryId }
    val initialDateText = remember { dateText }
    val initialNote = remember { note }

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
            if (money != null && currency != null) {
                amountText = AmountInput.fromMinor(money.amount, currency).displayText()
                initialAmountText = amountText
            }
        }
    }

    val account = accounts.firstOrNull { it.id == accountId }
    val currency = account?.let { currencyOf(it.currency, currencies.associateBy { c -> c.code.code }) }
    val kind = if (income) CategoryKind.INCOME else CategoryKind.EXPENSE
    val choices = categories.filter { it.kind == kind }

    val dirty = amountText != initialAmountText || note != initialNote || dateText != initialDateText ||
        categoryId != initialCategoryId || income != initialIncome

    fun requestClose() {
        if (dirty && !saving) confirmDiscard = true else onDone()
    }

    fun save() {
        if (saving) return
        val cur = currency ?: return run { error = "Выберите счёт" }
        // U01/U13: тот же строгий разбор, что у фильтра на Android — лишние знаки и мусор не обрезаются молча.
        val minor = when (val parsed = StrictAmountParser.parse(amountText, cur)) {
            is AmountParse.Value -> parsed.minor
            AmountParse.Empty -> null
            is AmountParse.Invalid -> return run {
                error = when (parsed.error) {
                    AmountParseError.TOO_MANY_DECIMALS ->
                        if (cur.minorUnits == 0) "У ${cur.code.code} нет дробной части" else "У ${cur.code.code} не больше ${cur.minorUnits} знаков после запятой"
                    AmountParseError.OVERFLOW -> "Слишком большое число"
                    AmountParseError.NEGATIVE, AmountParseError.NOT_A_NUMBER -> "Введите сумму больше нуля"
                }
            }
        }
        if (minor == null || minor.raw <= 0) return run { error = "Введите сумму больше нуля" }
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: return run { error = "Дата в формате ГГГГ-ММ-ДД" }
        val money = Money(minor, cur.code)
        val cat = categoryId?.takeIf { id -> choices.any { it.id == id } }
        val text = note.trim().ifBlank { null }
        saving = true
        scope.launch {
            runCatching {
                when {
                    editing != null -> c.txns.updateEntry(editing.id, account.id, money, cat, date, text)
                    income -> c.txns.addIncome(account.id, money, cat, date, text)
                    else -> c.txns.addExpense(account.id, money, cat, date, text)
                }
            }.onSuccess { onDone() }.onFailure { saving = false; error = it.message ?: "Не удалось сохранить" }
        }
    }

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Закрыть без сохранения?") },
            text = { Text("Введённое в этом окне не сохранится.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить ввод") } },
        )
        return
    }

    AlertDialog(
        // Esc и клик мимо окна — «безопасный выход»: изменённую форму не закрывают без вопроса.
        onDismissRequest = ::requestClose,
        title = { Text(if (editing != null) "Операция" else if (income) "Новый доход" else "Новый расход") },
        text = {
            Column(
                Modifier.width(560.dp).onPreviewKeyEvent {
                    // Enter сохраняет из любого поля и после выбора категории мышью (фокус уходит с суммы).
                    // Только нажатие: на отпускание save() второй раз записал бы дубль операции; save() сам
                    // проверяет форму и не пишет невалидное, а повтор во время записи игнорирует.
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter -> { if (it.type == KeyEventType.KeyDown) save(); true }
                        // Esc — безопасный выход: чистая форма закрывается, изменённая спрашивает.
                        Key.Escape -> { if (it.type == KeyEventType.KeyDown) requestClose(); true }
                        else -> false
                    }
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
        confirmButton = { TextButton(onClick = ::save, enabled = !saving) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = {
                        scope.launch { runCatching { c.txns.deleteById(editing.id) }.onSuccess { onDone() }.onFailure { error = it.message } }
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = ::requestClose) { Text("Отмена") }
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
