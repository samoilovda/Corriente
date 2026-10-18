package com.corriente.app.ui.txnentry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.platform.LocalContext
import com.corriente.app.UiPrefs
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.composed
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.ui.common.CategoryGlyph
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.model.Category
import com.corriente.money.CalcOp
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnEntryScreen(
    onDone: () -> Unit,
    onCreateAccount: () -> Unit = {},
    editingTxnId: String? = null,
    initialKind: EntryKind = EntryKind.EXPENSE,
    initialCategoryId: String? = null,
    initialAccountId: String? = null,
    repeatTxnId: String? = null,
    viewModel: TxnEntryViewModel = viewModel(
        factory = with(corrienteContainer()) {
            TxnEntryViewModel.factory(
                txnRepository, accountRepository, categoryRepository, currencyRepository, editingTxnId, initialKind,
                initialCategoryId, initialAccountId, repeatTxnId,
                pinnedEntries = UiPrefs.pinnedEntries(LocalContext.current),
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val message by viewModel.messages.collectAsState()
    val pendingUndo by viewModel.pendingUndo.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    var datePickerOpen by remember { mutableStateOf(false) }
    var categorySheetOpen by remember { mutableStateOf(false) }
    var discardDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(finished) { if (finished) onDone() }

    // U06: один путь выхода для стрелки, системного «Назад» и жеста. Есть изменения — спрашиваем.
    val guardExit = state.dirty && !finished && pendingUndo == null && !state.saving
    val requestExit = { if (guardExit) discardDialogOpen = true else onDone() }
    BackHandler(enabled = guardExit) { discardDialogOpen = true }

    // R5.3: снекбар «Операция удалена — Отменить» — держит форму на экране, пока не истечёт
    // таймер ViewModel (UNDO_WINDOW_MS) или пользователь не нажмёт «Отменить»; уход с экрана
    // раньше отменяет и эту корутину вместе с ViewModel — отмена уже невозможна.
    val undoMessage = stringResource(R.string.txn_deleted_undo_message)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(pendingUndo != null) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = snackbarState.showSnackbar(undoMessage, undoLabel, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isEditing) R.string.txn_entry_edit else R.string.txn_entry_title))
                },
                navigationIcon = {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        TextButton(onClick = viewModel::deleteEditing, enabled = pendingUndo == null) {
                            Text(stringResource(R.string.txn_entry_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // U06: на низком экране (альбом, IME, крупный шрифт) прокручивается всё; на обычном — только
        // верхняя часть, а клавиатура и основная кнопка всегда видны и не перекрываются.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 12.dp)) {
            val compact = maxHeight < COMPACT_HEIGHT
            val keyHeight = if (maxHeight < ROOMY_HEIGHT) 48.dp else 64.dp
            val outer = if (compact) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
            Column(outer) {
                val top = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                Column(top) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        SegmentedButton(
                            selected = state.kind == EntryKind.EXPENSE,
                            enabled = !viewModel.isEditing,
                            onClick = { viewModel.setKind(EntryKind.EXPENSE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stringResource(R.string.category_kind_expense)) }
                        SegmentedButton(
                            selected = state.kind == EntryKind.INCOME,
                            enabled = !viewModel.isEditing,
                            onClick = { viewModel.setKind(EntryKind.INCOME) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stringResource(R.string.category_kind_income)) }
                    }

                    if (state.loaded && state.accounts.isEmpty()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.txn_entry_no_accounts), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onCreateAccount) { Text(stringResource(R.string.txn_entry_create_account)) }
                        }
                    } else if (state.loaded) {
                        // UI-1.2 / UI-2.7: дата и счёт — чипами рядом. U03: у счёта всегда имя и валюта —
                        // два RUB-счёта различимы не по цвету, а по подписи.
                        FlowRow(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            AssistChip(
                                onClick = { datePickerOpen = true },
                                label = { Text(com.corriente.app.ui.common.dayLabel(state.date)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = stringResource(R.string.txn_entry_date),
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                            val account = state.selectedAccount
                            if (account != null) {
                                var accountChipMenuOpen by remember { mutableStateOf(false) }
                                val accountDescription = stringResource(R.string.txn_entry_account_cd, account.name, account.currency.code.code)
                                Box {
                                    AssistChip(
                                        onClick = { accountChipMenuOpen = true },
                                        label = {
                                            Text(
                                                "${account.name} · ${account.currency.code.code}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        modifier = Modifier.widthIn(max = 280.dp).semantics { contentDescription = accountDescription },
                                    )
                                    AccountMenu(
                                        expanded = accountChipMenuOpen,
                                        onDismiss = { accountChipMenuOpen = false },
                                        accounts = state.accounts,
                                        selectedAccountId = state.selectedAccountId,
                                        onSelect = { viewModel.selectAccount(it); accountChipMenuOpen = false },
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            TextField(
                                value = state.note,
                                onValueChange = viewModel::setNote,
                                placeholder = { Text(stringResource(R.string.txn_entry_note)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // R2.2: «частые» — тап заполняет вид/счёт/категорию/сумму целиком.
                        if (state.frequentOptions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            ChipRow(stringResource(R.string.txn_entry_frequent)) {
                                state.frequentOptions.forEach { option ->
                                    // E1: долгое нажатие (или действие TalkBack) закрепляет/открепляет шаблон.
                                    val pinToggle = stringResource(R.string.txn_entry_pin_toggle)
                                    FilterChip(
                                        selected = option.isPinned,
                                        onClick = { viewModel.applyFrequent(option) },
                                        label = { Text("${option.label} · ${option.amountText}") },
                                        leadingIcon = if (option.isPinned) {
                                            {
                                                Icon(
                                                    Icons.Filled.PushPin,
                                                    contentDescription = stringResource(R.string.txn_entry_pinned),
                                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .longPressInInitialPass { viewModel.togglePinned(option) }
                                            .semantics {
                                                customActions = listOf(
                                                    CustomAccessibilityAction(pinToggle) { viewModel.togglePinned(option); true },
                                                )
                                            },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (state.loaded && state.accounts.isNotEmpty()) {
                    // U06: сумма закреплена над клавиатурой — при крупном шрифте прокручивается только верх формы.
                    AmountBar(
                        amountText = state.amountText,
                        account = state.selectedAccount,
                        isIncome = state.kind == EntryKind.INCOME,
                        accounts = state.accounts,
                        selectedAccountId = state.selectedAccountId,
                        onSelectAccount = viewModel::selectAccount,
                        onBackspace = viewModel::pressBackspace,
                    )
                    if (state.nonPositiveResult || state.calcOverflow) {
                        Text(
                            stringResource(
                                if (state.calcOverflow) R.string.txn_entry_amount_too_large
                                else R.string.txn_entry_amount_must_be_positive,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Keypad(
                        onDigit = viewModel::pressDigit,
                        onDecimalPoint = viewModel::pressDecimalPoint,
                        onOp = viewModel::pressOp,
                        onEquals = viewModel::pressEquals,
                        equalsEnabled = state.hasPendingCalc,
                        keyHeight = keyHeight,
                    )
                    Spacer(Modifier.height(8.dp))
                    // U02: категория — поле (только выбирает), запись — всегда отдельной основной кнопкой.
                    val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
                    val quickCategory = selectedCategory?.takeIf { initialCategoryId != null && !viewModel.isEditing }
                    val interactive = !state.saving && pendingUndo == null
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        CategoryField(
                            category = selectedCategory,
                            enabled = interactive,
                            onClick = { categorySheetOpen = true },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        Button(
                            onClick = { viewModel.save() },
                            enabled = state.canSave && interactive,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).fillMaxHeight(),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.txn_entry_saving))
                            } else {
                                Text(
                                    when {
                                        viewModel.isEditing -> stringResource(R.string.save)
                                        quickCategory != null -> stringResource(R.string.txn_entry_add_to_category, quickCategory.name)
                                        state.kind == EntryKind.INCOME -> stringResource(R.string.txn_entry_add_income)
                                        else -> stringResource(R.string.txn_entry_add_expense)
                                    },
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text(stringResource(R.string.txn_entry_discard_title)) },
            text = { Text(stringResource(R.string.txn_entry_discard_body)) },
            confirmButton = {
                TextButton(onClick = { discardDialogOpen = false; onDone() }) {
                    Text(stringResource(R.string.txn_entry_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text(stringResource(R.string.txn_entry_continue)) }
            },
        )
    }

    // U02: выбор категории только выбирает — и при создании, и при правке.
    if (categorySheetOpen) {
        ModalBottomSheet(onDismissRequest = { categorySheetOpen = false }) {
            Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
                CategoryGrid(
                    label = stringResource(R.string.txn_entry_category),
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = { id ->
                        categorySheetOpen = false
                        viewModel.selectCategory(id)
                    },
                )
            }
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    datePickerOpen = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** U06: ниже — прокручивается вся форма; ниже [ROOMY_HEIGHT] — клавиши 48dp вместо 64dp. */
private val COMPACT_HEIGHT = 480.dp
private val ROOMY_HEIGHT = 600.dp

/** U02: поле категории — показывает выбор (иконка, цвет, имя) или «Без категории»; тап открывает выбор. */
@Composable
private fun CategoryField(category: Category?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.txn_entry_category)
    val value = category?.name ?: stringResource(R.string.txn_entry_no_category)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = "$label: $value" },
    ) {
        if (category != null && !category.icon.isNullOrBlank()) {
            val tint = if (category.color != 0) Color(category.color) else MaterialTheme.colorScheme.onSurfaceVariant
            CategoryGlyph(category.icon, category.name, tint, size = 22.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * F2.6: категории — сетка «иконка + подпись + цвет». U10: колонки по ширине (не меньше 88dp на ячейку),
 * поиск при длинном справочнике; «Без категории» виден всегда, в том числе во время поиска.
 */
@Composable
private fun CategoryGrid(
    label: String,
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val archivedFmt = stringResource(R.string.entry_archived_suffix, "%s")
    var query by remember { mutableStateOf("") }
    val shown = remember(categories, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) categories else categories.filter { it.name.lowercase().contains(needle) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (categories.size > CATEGORY_SEARCH_THRESHOLD) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.txn_entry_category_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item(key = "none") {
                CategoryCell(
                    text = stringResource(R.string.txn_entry_no_category),
                    icon = null,
                    color = 0,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(shown, key = { it.id }) { category ->
                CategoryCell(
                    text = if (category.isArchived) archivedFmt.format(category.name) else category.name,
                    icon = category.icon?.takeIf { it.isNotBlank() },
                    color = category.color,
                    selected = selectedId == category.id,
                    onClick = { onSelect(category.id) },
                )
            }
        }
        if (shown.isEmpty() && query.isNotBlank()) {
            Text(
                stringResource(R.string.txn_entry_category_none_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** U10: поиск появляется, когда категорий больше, чем помещается в пару рядов сетки. */
private const val CATEGORY_SEARCH_THRESHOLD = 8

@Composable
private fun CategoryCell(
    text: String,
    icon: String?,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
            )
            // U08: TalkBack слышит «выбрано» — выбор виден не только рамкой.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val tint = if (color != 0) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) CategoryGlyph(icon, text, tint, size = 18.dp)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun AccountMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    accounts: List<AccountOption>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        accounts.forEach { option ->
            val label = if (option.isArchived) {
                stringResource(R.string.entry_archived_suffix, option.name)
            } else {
                option.name
            }
            DropdownMenuItem(
                text = { Text("$label · ${option.currency.code.code}") },
                onClick = { onSelect(option.id) },
                trailingIcon = if (option.id == selectedAccountId) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Плашка суммы: слева «таблетка» счёта с кодом валюты (выбор счёта), справа сумма и «⌫».
 * Цвета — из темы (Material You): расход — основной контейнер, доход — третичный (контраст on*Container
 * к своему контейнеру в схемах Material — не ниже 4.5:1, жёстких цветов здесь нет).
 * U03: TalkBack слышит имя счёта и валюту, а не только код.
 */
@Composable
private fun AmountBar(
    amountText: String,
    account: AccountOption?,
    isIncome: Boolean,
    accounts: List<AccountOption>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val currencyCode = account?.currency?.code?.code.orEmpty()
    val accountDescription = account?.let { stringResource(R.string.txn_entry_account_cd, it.name, currencyCode) }
        ?: stringResource(R.string.txn_entry_account)
    Surface(
        color = if (isIncome) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (isIncome) colors.onTertiaryContainer else colors.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        ) {
            Box {
                Surface(
                    onClick = { menuOpen = true },
                    shape = CircleShape,
                    color = LocalContentColor.current.copy(alpha = 0.12f),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = accountDescription },
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(currencyCode, style = MaterialTheme.typography.labelLarge)
                    }
                }
                AccountMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelect = { onSelectAccount(it); menuOpen = false },
                )
            }
            // UI-3.1: короткий кросс-фейд при вводе цифры.
            AnimatedContent(
                targetState = amountText,
                transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(90)) },
                contentAlignment = Alignment.CenterEnd,
                label = "amount-text",
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            ) { shown ->
                Text(
                    text = shown,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$shown $currencyCode" },
                )
            }
            IconButton(onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.txn_entry_backspace))
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimalPoint: () -> Unit,
    onOp: (CalcOp) -> Unit,
    onEquals: () -> Unit,
    equalsEnabled: Boolean,
    keyHeight: androidx.compose.ui.unit.Dp = 64.dp,
) {
    // Раскладка как в Monefy: цифры слева, операции — правой колонкой; «=» в нижней строке.
    val rows = listOf(
        listOf("1", "2", "3", "+"),
        listOf("4", "5", "6", "−"),
        listOf("7", "8", "9", "×"),
        listOf(".", "0", "=", "÷"),
    )
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    // U08: TalkBack читает «минус», а не символ «−»; цифры озвучиваются как есть.
    val spoken = mapOf(
        "+" to stringResource(R.string.calc_key_plus),
        "−" to stringResource(R.string.calc_key_minus),
        "×" to stringResource(R.string.calc_key_times),
        "÷" to stringResource(R.string.calc_key_divide),
        "=" to stringResource(R.string.calc_key_equals),
        "." to stringResource(R.string.calc_key_decimal),
    )
    // Высота клавиши фиксирована в dp — цифру растим вместе с ней, а не с fontScale, иначе при 200%
    // она не помещается в клавишу (сами символы и так крупные).
    val keyFont = with(LocalDensity.current) { (keyHeight.value * 0.44f / fontScale).sp }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isOp = key in setOf("+", "−", "×", "÷")
                    if (key == "=" && !equalsEnabled) {
                        // UI-2.7: «=» появляется, только когда есть что вычислять; место остаётся пустым.
                        Spacer(Modifier.weight(1f).height(keyHeight))
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                when (key) {
                                    "." -> onDecimalPoint()
                                    "=" -> onEquals()
                                    "+" -> onOp(CalcOp.PLUS)
                                    "−" -> onOp(CalcOp.MINUS)
                                    "×" -> onOp(CalcOp.TIMES)
                                    "÷" -> onOp(CalcOp.DIVIDE)
                                    else -> onDigit(key[0])
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            colors = when {
                                key == "=" -> ButtonDefaults.buttonColors()
                                isOp -> ButtonDefaults.filledTonalButtonColors()
                                else -> ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    contentColor = colors.onSurface,
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(1f).height(keyHeight)
                                .then(spoken[key]?.let { label -> Modifier.semantics { contentDescription = label } } ?: Modifier),
                        ) {
                            Text(key, fontSize = keyFont, fontWeight = if (isOp) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

/**
 * E1: долгое нажатие поверх чипа. Внутренний `clickable` чипа забирает «нажатие» раньше родителя, поэтому слушаем
 * в проходе Initial; после срабатывания поглощаем отпускание — иначе чип ещё и применил бы шаблон.
 */
private fun Modifier.longPressInInitialPass(onLongPress: () -> Unit): Modifier = composed {
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    pointerInput(onLongPress) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = withTimeoutOrNull(timeout) { waitForUpOrCancellation(PointerEventPass.Initial) }
            if (up == null && currentEvent.changes.any { it.pressed }) {
                onLongPress()
                // Поглотить остаток жеста, чтобы чип не посчитал его тапом.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}
