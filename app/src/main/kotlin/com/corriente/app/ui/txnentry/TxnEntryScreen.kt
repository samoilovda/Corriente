package com.corriente.app.ui.txnentry

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.selected
import com.corriente.data.quickentry.FAVOURITE_CATEGORIES_LIMIT
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Dp
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
import com.corriente.app.ui.theme.LocalCorrienteColors
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    /** Если задано — в панели категорий последней ячейкой «+ Категория» (переход к справочнику, назад — в форму). */
    onCreateCategory: (() -> Unit)? = null,
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
                favouriteCategories = UiPrefs.favouriteCategories(LocalContext.current),
                lastAccount = UiPrefs.lastAccount(LocalContext.current),
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
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    var discardDialogOpen by remember { mutableStateOf(false) }
    val showKindLabels = UiPrefs.showKindLabels(LocalContext.current)

    LaunchedEffect(finished) { if (finished) onDone() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // U06: один путь выхода для стрелки, системного «Назад» и жеста. Есть изменения — спрашиваем.
    val guardExit = state.dirty && !finished && pendingUndo == null && !state.saving
    val requestExit = { if (guardExit) discardDialogOpen = true else onDone() }
    BackHandler(enabled = categoriesOpen) { categoriesOpen = false }
    BackHandler(enabled = guardExit && !categoriesOpen) { discardDialogOpen = true }

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
                // Пока открыт список категорий, верх сжимается до выбора вида/даты/счёта, а всё освободившееся место
                // отдаётся категориям.
                val top = when {
                    compact || categoriesOpen -> Modifier.fillMaxWidth()
                    else -> Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                }
                Column(top) {
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
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            KindToggle(
                                kind = state.kind,
                                enabled = true,
                                showLabels = showKindLabels,
                                onKind = viewModel::setKind,
                            )
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

                        AnimatedVisibility(visible = !categoriesOpen) {
                            Column {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
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
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
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
                    // Свайп вниз по списку категорий (когда он уже в начале) возвращает клавиатуру.
                    val closeThresholdPx = with(LocalDensity.current) { CATEGORY_SWIPE_THRESHOLD.toPx() }
                    val closeOnPullDown = remember(closeThresholdPx) {
                        object : NestedScrollConnection {
                            var pulled = 0f
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (available.y < 0f) pulled = 0f
                                return Offset.Zero
                            }
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput && available.y > 0f) pulled += available.y
                                return Offset.Zero
                            }
                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                if (pulled >= closeThresholdPx) categoriesOpen = false
                                pulled = 0f
                                return Velocity.Zero
                            }
                        }
                    }
                    // Категории занимают место клавиатуры: она уезжает вверх, панель выезжает снизу.
                    AnimatedContent(
                        modifier = if (categoriesOpen && !compact) Modifier.weight(1f) else Modifier,
                        targetState = categoriesOpen,
                        transitionSpec = {
                            if (targetState) {
                                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                            } else {
                                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                            }.using(SizeTransform(clip = true))
                        },
                        label = "keypadOrCategories",
                    ) { showCategories ->
                        if (showCategories) {
                            Column(
                                (if (compact) Modifier.fillMaxWidth().height(keyHeight * 4 + 24.dp) else Modifier.fillMaxSize())
                                    .nestedScroll(closeOnPullDown),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().height(20.dp)
                                        .verticalSwipe(true, CATEGORY_SWIPE_THRESHOLD, onUp = {}, onDown = { categoriesOpen = false }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.width(36.dp).height(4.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                                    )
                                }
                                CategoryGrid(
                                    categories = state.categories,
                                    onCreateCategory = onCreateCategory,
                                    onReorder = viewModel::reorderCategories,
                                    favourites = state.favouriteCategories.takeIf { state.favouritesEnabled },
                                    onAddFavourite = viewModel::addFavourite,
                                    onRemoveFavourite = viewModel::removeFavourite,
                                    selectedId = state.selectedCategoryId,
                                    onSelect = { id ->
                                        categoriesOpen = false
                                        viewModel.selectCategoryAndSave(id)
                                    },
                                )
                            }
                        } else {
                            Keypad(
                                onDigit = viewModel::pressDigit,
                                onDecimalPoint = viewModel::pressDecimalPoint,
                                onOp = viewModel::pressOp,
                                onEquals = viewModel::pressEquals,
                                equalsEnabled = state.hasPendingCalc,
                                onSwipeUp = { categoriesOpen = true },
                                keyHeight = keyHeight,
                            )
                        }
                    }
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
                            onToggle = { categoriesOpen = !categoriesOpen },
                            onSwipeUp = { categoriesOpen = true },
                            onSwipeDown = { categoriesOpen = false },
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

/** Путь свайпа по кнопке «Категория», после которого панель категорий открывается (вверх) или закрывается (вниз). */
private val CATEGORY_SWIPE_THRESHOLD = 24.dp

/** На клавиатуре порог больше: при быстром наборе палец сносит вбок и вверх, это не должно открывать категории. */
private val KEYPAD_SWIPE_THRESHOLD = 56.dp

/** Вертикальный свайп: вверх — [onUp], вниз — [onDown]; короткие движения игнорируются. */
private fun Modifier.verticalSwipe(enabled: Boolean, threshold: Dp, onUp: () -> Unit, onDown: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val thresholdPx = threshold.toPx()
        var dragged = 0f
        detectVerticalDragGestures(
            onDragStart = { dragged = 0f },
            onVerticalDrag = { change, dy -> change.consume(); dragged += dy },
            onDragEnd = {
                if (dragged <= -thresholdPx) onUp() else if (dragged >= thresholdPx) onDown()
            },
        )
    }

/**
 * U02: поле категории — показывает выбор (иконка, цвет, имя) или «Без категории». Тап переключает панель
 * категорий, свайп вверх открывает её (клавиатура уезжает вверх), свайп вниз возвращает клавиатуру.
 */
@Composable
private fun CategoryField(
    category: Category?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.txn_entry_category)
    val value = category?.name ?: stringResource(R.string.txn_entry_no_category)
    val surfaceColor = if (category != null && category.color != 0) {
        Color(category.color).copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val swipe = Modifier.verticalSwipe(enabled, CATEGORY_SWIPE_THRESHOLD, onSwipeUp, onSwipeDown)
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
        modifier = modifier
            .then(swipe)
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "$label: $value" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (category != null) {
                val tint = if (category.color != 0) Color(category.color) else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CategoryGlyph(category.icon, category.name, tint, size = 20.dp)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * F2.6: категории — сетка «иконка + подпись + цвет». U10: колонки по ширине (не меньше 88dp на ячейку),
 * поиск при длинном справочнике; «Без категории» виден всегда, в том числе во время поиска.
 */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreateCategory: (() -> Unit)? = null,
    onReorder: (List<String>) -> Unit = {},
    favourites: List<Category>? = null,
    onAddFavourite: (String) -> Unit = {},
    onRemoveFavourite: (String) -> Unit = {},
) {
    val archivedFmt = stringResource(R.string.entry_archived_suffix, "%s")
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    // Избранные закреплены отдельной строкой над сеткой; при поиске строка прячется и ищем по всем категориям.
    val favouritesShown = if (favourites != null && !searching) favourites else emptyList()
    val favouriteIds = favouritesShown.map { it.id }
    val shown = remember(categories, query, favouriteIds) {
        val needle = query.trim().lowercase()
        categories.filter { it.id !in favouriteIds && (needle.isEmpty() || it.name.lowercase().contains(needle)) }
    }
    var favouritePickerOpen by remember { mutableStateOf(false) }
    var favouriteMenuId by remember { mutableStateOf<String?>(null) }
    // Перетаскивание как в лончере: долгое нажатие поднимает плитку, дальше она следует за пальцем, остальные
    // расступаются. Порядок меняется локально во время жеста и уходит в БД одним вызовом при отпускании.
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    var order by remember(shown) { mutableStateOf(shown.map { it.id }) }
    val byId = remember(shown) { shown.associateBy { it.id } }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    fun cellAt(position: Offset): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        info.key != CATEGORY_NONE_KEY &&
            position.x >= info.offset.x && position.x < info.offset.x + info.size.width &&
            position.y >= info.offset.y && position.y < info.offset.y + info.size.height
    }
    fun moveDraggedUnderPointer() {
        val dragged = draggingId ?: return
        val target = cellAt(dragPos)?.key as? String ?: return
        if (target == dragged) return
        val from = order.indexOf(dragged)
        val to = order.indexOf(target)
        if (from < 0 || to < 0) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        // Палец у края сетки — прокручиваем её к скрытым категориям, пока плитка не поставлена на место.
        while (true) {
            val height = gridState.layoutInfo.viewportSize.height
            val step = when {
                dragPos.y < CATEGORY_DRAG_EDGE_PX -> -CATEGORY_DRAG_SCROLL_PX
                dragPos.y > height - CATEGORY_DRAG_EDGE_PX -> CATEGORY_DRAG_SCROLL_PX
                else -> 0f
            }
            if (step != 0f) {
                gridState.scrollBy(step)
                moveDraggedUnderPointer()
            }
            delay(16)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (favourites != null && !searching) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(FAVOURITE_CATEGORIES_LIMIT) { index ->
                    val favourite = favourites.getOrNull(index)
                    Box(Modifier.weight(1f)) {
                        if (favourite != null) {
                            CategoryCell(
                                text = favourite.name,
                                icon = favourite.icon?.takeIf { it.isNotBlank() },
                                color = favourite.color,
                                showGlyph = true,
                                selected = selectedId == favourite.id,
                                onClick = { onSelect(favourite.id) },
                                onLongClick = { favouriteMenuId = favourite.id },
                            )
                            DropdownMenu(expanded = favouriteMenuId == favourite.id, onDismissRequest = { favouriteMenuId = null }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.txn_entry_favourite_remove)) },
                                    onClick = { favouriteMenuId = null; onRemoveFavourite(favourite.id) },
                                )
                            }
                        } else {
                            val addDescription = stringResource(R.string.txn_entry_favourite_add)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClickLabel = addDescription) { favouritePickerOpen = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = addDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 76.dp),
            state = gridState,
            // Во время перетаскивания сетка не должна прокручиваться жестом — прокрутку делает край-триггер выше.
            userScrollEnabled = draggingId == null,
            modifier = Modifier.fillMaxWidth().weight(1f).pointerInput(shown, searching) {
                if (searching) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val hit = cellAt(offset)?.key as? String
                        if (hit != null) {
                            draggingId = hit
                            dragPos = offset
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (draggingId != null) {
                            change.consume()
                            dragPos += amount
                            moveDraggedUnderPointer()
                        }
                    },
                    onDragEnd = {
                        val newOrder = order
                        draggingId = null
                        // Избранные идут первыми: так порядок остаётся цельным, если настройку потом выключить.
                        if (newOrder != shown.map { it.id }) onReorder(favouriteIds + newOrder)
                    },
                    onDragCancel = {
                        draggingId = null
                        order = shown.map { it.id }
                    },
                )
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            item(key = CATEGORY_NONE_KEY) {
                CategoryCell(
                    text = stringResource(R.string.txn_entry_no_category),
                    icon = null,
                    color = 0,
                    showGlyph = false,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(order, key = { it }) { id ->
                val category = byId[id] ?: return@items
                val dragged = id == draggingId
                CategoryCell(
                    text = if (category.isArchived) archivedFmt.format(category.name) else category.name,
                    icon = category.icon?.takeIf { it.isNotBlank() },
                    color = category.color,
                    showGlyph = true,
                    selected = selectedId == category.id,
                    onClick = { onSelect(category.id) },
                    modifier = if (dragged) {
                        Modifier.zIndex(1f).graphicsLayer {
                            // Поднятая плитка центрируется под пальцем, а не остаётся в своей ячейке.
                            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                            if (info != null) {
                                translationX = dragPos.x - (info.offset.x + info.size.width / 2f)
                                translationY = dragPos.y - (info.offset.y + info.size.height / 2f)
                            }
                            scaleX = 1.08f
                            scaleY = 1.08f
                            alpha = 0.92f
                        }
                    } else {
                        Modifier.animateItem()
                    },
                )
            }
        }
        if (onCreateCategory != null && query.isBlank()) {
            TextButton(onClick = onCreateCategory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.txn_entry_new_category))
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
    if (favouritePickerOpen) {
        AlertDialog(
            onDismissRequest = { favouritePickerOpen = false },
            title = { Text(stringResource(R.string.txn_entry_favourite_add)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    categories.filter { it.id !in favouriteIds }.forEach { category ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { favouritePickerOpen = false; onAddFavourite(category.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val tint = if (category.color != 0) Color(category.color) else MaterialTheme.colorScheme.onSurfaceVariant
                            Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                                CategoryGlyph(category.icon?.takeIf { it.isNotBlank() }, category.name, tint, size = 18.dp)
                            }
                            Text(category.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { favouritePickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** U10: поиск появляется, когда категорий больше, чем помещается в пару рядов сетки. */
private const val CATEGORY_SEARCH_THRESHOLD = 8

/** Ключ плитки «Без категории» в сетке: её не переставляют и не подменяют при перетаскивании. */
private const val CATEGORY_NONE_KEY = "none"

/** Перетаскивание: расстояние до края сетки, с которого начинается автопрокрутка, и её шаг за кадр (px). */
private const val CATEGORY_DRAG_EDGE_PX = 72f
private const val CATEGORY_DRAG_SCROLL_PX = 14f

@Composable
private fun CategoryCell(
    text: String,
    icon: String?,
    color: Int,
    showGlyph: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderModifier = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bgColor, shape)
            .then(borderModifier)
            // U08: TalkBack слышит «выбрано» — выбор виден не только рамкой.
            .then(
                if (onLongClick == null) {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier.combinedClickable(role = Role.RadioButton, onClick = onClick, onLongClick = onLongClick)
                        .semantics { this.selected = selected }
                },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val tint = if (color != 0) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (showGlyph) CategoryGlyph(icon, text, tint, size = 18.dp)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        ) {
            Box {
                Surface(
                    onClick = { menuOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
                val amountFontSize = when {
                    shown.length > 13 -> 26.sp
                    shown.length > 9 -> 32.sp
                    else -> 40.sp
                }
                Text(
                    text = shown,
                    style = LocalTextStyle.current.copy(
                        fontSize = amountFontSize,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
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
    onSwipeUp: () -> Unit,
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
    // Свайп вверх по клавиатуре открывает категории (клавиатура уезжает вверх).
    Column(
        Modifier.verticalSwipe(true, KEYPAD_SWIPE_THRESHOLD, onUp = onSwipeUp, onDown = {}),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isOp = key in setOf("+", "−", "×", "÷")
                    run {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val keyRadius by animateDpAsState(
                            targetValue = if (isPressed) 12.dp else 24.dp,
                            label = "keypad-shape",
                        )
                        val buttonColors = when {
                            // «=» всегда на месте; пока считать нечего — приглушён.
                            key == "=" -> ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                                disabledContainerColor = colors.primary.copy(alpha = 0.14f),
                                disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
                            )
                            isOp -> ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer,
                            )
                            else -> ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.surfaceContainerLow,
                                contentColor = colors.onSurface,
                            )
                        }
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
                            enabled = key != "=" || equalsEnabled,
                            shape = RoundedCornerShape(keyRadius),
                            colors = buttonColors,
                            interactionSource = interactionSource,
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

/**
 * Переключатель расход/доход. В первый запуск ([UiPrefs.showKindLabels]) — во всю ширину со словами, дальше —
 * компактная пара «−»/«+». Обе кнопки слегка тонированы цветами денег темы: расход красноватый, доход зеленоватый;
 * выбранная — насыщеннее.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindToggle(kind: EntryKind, enabled: Boolean, showLabels: Boolean, onKind: (EntryKind) -> Unit) {
    val money = LocalCorrienteColors.current
    // В одну строку с чипами даты и счёта — той же высоты (32dp), без отдельной полосы над формой.
    val rowModifier = if (showLabels) Modifier.width(COMPACT_TOGGLE_WIDTH + 40.dp) else Modifier.width(COMPACT_TOGGLE_WIDTH)
    SingleChoiceSegmentedButtonRow(rowModifier.height(32.dp)) {
        listOf(EntryKind.EXPENSE, EntryKind.INCOME).forEachIndexed { index, option ->
            val tone = if (option == EntryKind.EXPENSE) money.expense else money.income
            val label = stringResource(
                if (option == EntryKind.EXPENSE) R.string.category_kind_expense else R.string.category_kind_income,
            )
            SegmentedButton(
                selected = kind == option,
                enabled = enabled,
                onClick = { onKind(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = tone.copy(alpha = 0.22f),
                    activeContentColor = tone,
                    inactiveContainerColor = tone.copy(alpha = 0.12f),
                    inactiveContentColor = tone,
                ),
                // Компактно — без галочки выбранного: её место съело бы «−»/«+», выбор и так виден по заливке.
                icon = { if (showLabels) SegmentedButtonDefaults.Icon(active = kind == option) },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                if (showLabels) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                } else {
                    Text(if (option == EntryKind.EXPENSE) "−" else "+", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private val COMPACT_TOGGLE_WIDTH = 88.dp
