package com.corriente.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corriente.data.model.Txn
import com.corriente.data.usecase.MonthOverview
import com.corriente.data.usecase.buildMonthOverview
import java.time.YearMonth

/** Что открыть в окне ввода: новая операция (вид, категория, счёт) или правка существующей. */
sealed interface EntryRequest {
    data class New(val income: Boolean, val categoryId: String? = null, val accountId: String? = null) : EntryRequest
    data class Edit(val txn: Txn) : EntryRequest
}

@Composable
fun TransactionsScreen(c: DesktopContainer, onEntry: (EntryRequest) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var currency by remember { mutableStateOf<String?>(null) }
    // U13: поиск — та же область, что на Android (заметка, категория, счёт; TxnRepository.search) и по всей истории.
    var query by remember { mutableStateOf("") }
    val found by remember(query) {
        if (query.isBlank()) kotlinx.coroutines.flow.flowOf(null) else c.txns.search(query.trim())
    }.collectAsState(null)

    val monthTxns by remember(month) { c.txns.observeRange(month.atDay(1), month.atEndOfMonth()) }.collectAsState(emptyList())
    val accounts by c.accounts.observeAll().collectAsState(emptyList())
    val categories by c.categories.observeAllForLookup().collectAsState(emptyList())
    val currencies by c.currencies.observeAll().collectAsState(emptyList())
    val balances by c.balances.observeBalances().collectAsState(emptyList())
    val pinned by c.overviewSettings.pinned.collectAsState(emptyList())
    val byCode = currencies.associateBy { it.code.code }

    val overview = buildMonthOverview(month, monthTxns, accounts, categories, currencies, balances, accountId, currency, pinned)

    if (overview == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Счетов пока нет. Подключите синхронизацию с телефоном (раздел «Синхронизация») — данные придут оттуда.")
        }
        return
    }

    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    val searching = query.isNotBlank()
    val source = if (searching) found.orEmpty() else monthTxns
    val visible = source.filter { t ->
        when {
            overview.accountId != null -> when (t) {
                is Txn.Expense -> t.accountId == overview.accountId
                is Txn.Income -> t.accountId == overview.accountId
                is Txn.Transfer -> t.fromAccountId == overview.accountId || t.toAccountId == overview.accountId
            }
            else -> when (t) {
                is Txn.Expense -> t.amount.currency.code == overview.currencyCode
                is Txn.Income -> t.amount.currency.code == overview.currencyCode
                is Txn.Transfer -> t.fromAmount.currency.code == overview.currencyCode || t.toAmount.currency.code == overview.currencyCode
            }
        }
    }.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.createdAt })

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(400.dp).fillMaxHeight().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountSelector(overview, onAccount = { accountId = it }, onCurrency = { accountId = null; currency = it })
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий месяц") }
                Text("${MONTHS[month.monthValue - 1]} ${month.year}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц") }
            }
            RingWithIcons(overview) { categoryId -> onEntry(EntryRequest.New(income = false, categoryId = categoryId, accountId = overview.accountId)) }
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text("Баланс  ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        overview.balanceText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overview.balanceNegative) EXPENSE_COLOR else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onEntry(EntryRequest.New(income = false, accountId = overview.accountId)) },
                    colors = ButtonDefaults.buttonColors(containerColor = EXPENSE_COLOR),
                ) { Text("−  Расход") }
                Button(
                    onClick = { onEntry(EntryRequest.New(income = true, accountId = overview.accountId)) },
                    colors = ButtonDefaults.buttonColors(containerColor = INCOME_COLOR),
                ) { Text("+  Доход") }
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Поиск по заметке, категории и счёту") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search")
                // Esc в поле поиска очищает запрос, а не закрывает окно.
                .onPreviewKeyEvent {
                    if (it.key == Key.Escape && query.isNotEmpty()) {
                        if (it.type == KeyEventType.KeyDown) query = ""
                        true
                    } else {
                        false
                    }
                },
        )
        Text(
            if (searching) {
                "Найдено во всей истории (${overview.accountName ?: "все счета · ${overview.currencyCode}"}): ${visible.size}"
            } else {
                "${MONTHS[month.monthValue - 1]} ${month.year}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searching) "Ничего не найдено" else "В этом месяце операций нет")
            }
        } else {
            val days = visible.groupBy { it.date }
            LazyColumn(Modifier.fillMaxSize()) {
                days.forEach { (date, dayTxns) ->
                    item(key = "d-$date") {
                        Text(
                            dayLabel(date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(dayTxns, key = { it.id }) { t ->
                        TxnRow(t, categoryById, accountById, byCode) { if (t !is Txn.Transfer) onEntry(EntryRequest.Edit(t)) }
                        HorizontalDivider()
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TxnRow(
    t: Txn,
    categoryById: Map<String, com.corriente.data.model.Category>,
    accountById: Map<String, com.corriente.data.model.Account>,
    byCode: Map<String, com.corriente.money.Currency>,
    onClick: () -> Unit,
) {
    val (title, icon, color, amount, accountName) = when (t) {
        is Txn.Expense -> {
            val cat = t.categoryId?.let(categoryById::get)
            Row5(cat?.name ?: "Без категории", cat?.icon, cat?.color ?: 0, "−" + formatMoney(t.amount, byCode), accountById[t.accountId]?.name.orEmpty())
        }
        is Txn.Income -> {
            val cat = t.categoryId?.let(categoryById::get)
            Row5(cat?.name ?: "Без категории", cat?.icon, cat?.color ?: 0, "+" + formatMoney(t.amount, byCode), accountById[t.accountId]?.name.orEmpty())
        }
        is Txn.Transfer -> Row5(
            "${accountById[t.fromAccountId]?.name ?: "?"} → ${accountById[t.toAccountId]?.name ?: "?"}",
            "mi:swap_horiz", 0,
            "${formatMoney(t.fromAmount, byCode)} → ${formatMoney(t.toAmount, byCode)}",
            "",
        )
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(icon, title, color)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(t.note?.takeIf { it.isNotBlank() }, accountName.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(
            amount,
            color = when (t) {
                is Txn.Expense -> MaterialTheme.colorScheme.onSurface
                is Txn.Income -> INCOME_COLOR
                is Txn.Transfer -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private data class Row5(val title: String, val icon: String?, val color: Int, val amount: String, val account: String)

@Composable
private fun AccountSelector(overview: MonthOverview, onAccount: (String) -> Unit, onCurrency: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(overview.accountName ?: "Все счета · ${overview.currencyCode}")
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            overview.currencyOptions.forEach { code ->
                DropdownMenuItem(text = { Text("Все счета · $code") }, onClick = { open = false; onCurrency(code) })
            }
            HorizontalDivider()
            overview.accountOptions.forEach { a ->
                DropdownMenuItem(text = { Text("${a.name} · ${a.currency.code}") }, onClick = { open = false; onAccount(a.id) })
            }
        }
    }
}

