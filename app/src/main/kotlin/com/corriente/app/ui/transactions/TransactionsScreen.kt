package com.corriente.app.ui.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.corriente.app.ui.common.groupItemShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.withTimeoutOrNull
import com.corriente.app.ui.common.MoneyKind
import com.corriente.app.ui.txnentry.EntryKind
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SearchOff
import com.corriente.app.ui.common.EmptyState
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHost
import com.corriente.app.ui.common.rememberMessageSnackbarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.UiPrefs
import com.corriente.app.ui.common.CategoryGlyph
import com.corriente.app.ui.report.ResourceReportTexts
import com.corriente.app.corrienteContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddTransfer: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onEditTransfer: (String) -> Unit,
    /** UI-3.2: свайп вправо — «Повторить»: ввод с той же категорией/счётом/суммой и сегодняшней датой. */
    onRepeatTransaction: (id: String, kind: EntryKind) -> Unit = { _, _ -> },
    /** Тап по иконке категории в шапке: ввод расхода (или дохода — на кольце доходов) в эту категорию и выбранный в шапке счёт. */
    onAddInCategory: (kind: EntryKind, categoryId: String, accountId: String?) -> Unit = { kind, _, _ ->
        if (kind == EntryKind.INCOME) onAddIncome() else onAddExpense()
    },
    /** U07: планирование — из меню «Операций», не только из настроек. */
    onOpenBudgets: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    /** U11: стартовая карточка пустой базы. */
    onCreateAccount: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    viewModel: TransactionsViewModel = viewModel(
        factory = with(corrienteContainer()) {
            TransactionsViewModel.factory(
                txnRepository, accountRepository, categoryRepository, currencyRepository,
                ResourceReportTexts(LocalContext.current).csvHeaders,
                stringResource(R.string.adjust_row_title),
                UiPrefs.startAccountSettings(LocalContext.current),
            )
        },
    ),
    overviewViewModel: MonthOverviewViewModel = viewModel(
        factory = with(corrienteContainer()) {
            MonthOverviewViewModel.factory(
                txnRepository, accountRepository, categoryRepository, currencyRepository, accountBalanceUseCase,
                overviewCategoriesSettings,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val overview by overviewViewModel.uiState.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Счёт в шапке и фильтр списка по счёту — одно и то же (как в Monefy: выбранный счёт видно и в кольце, и в списке).
    LaunchedEffect(state.filter.accountId) { overviewViewModel.setAccount(state.filter.accountId) }
    // Запуск с «Все счета» в валюте последней операции или с последним выбором в шапке: валюта шапки, список не сужаем.
    val startCurrency by viewModel.startCurrency.collectAsState()
    LaunchedEffect(startCurrency) {
        startCurrency?.let { code ->
            overviewViewModel.setAllAccounts(code)
            viewModel.consumeStartCurrency()
        }
    }
    // Месяц в кольце глубже подгруженного окна списка — подгружаем историю до него.
    val overviewMonth = overview.overview?.month
    // Шапка с кольцом приходит позже первого кадра: список держит позицию по ключу и без этого остаётся
    // прокрученным мимо неё — возвращаем наверх, если пользователь сам ещё ничего не листал.
    val hasHeader = overview.overview != null && state.filter.query.isBlank()
    LaunchedEffect(hasHeader) {
        if (hasHeader && !listState.isScrollInProgress) listState.scrollToItem(0)
    }
    LaunchedEffect(overviewMonth) { overviewMonth?.let(viewModel::ensureWindowCovers) }
    // Экран открыт через полночь/границу месяца — шапка переезжает на сегодняшний месяц.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) overviewViewModel.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val message by viewModel.messages.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    // UI-3.2: «Удалено · Отменить» на 5 секунд; по истечении удаление окончательно.
    val pendingUndo by viewModel.pendingUndo.collectAsState()
    val undoMessage = stringResource(R.string.txn_deleted_undo_message)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
            snackbarState.showSnackbar(undoMessage, undoLabel, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.consumeUndo()
    }
    // Только что записанная операция из формы: «Записано — Отменить» на 5 секунд.
    val savedFormat = stringResource(R.string.txn_saved_message, "%s")
    LaunchedEffect(Unit) {
        com.corriente.app.ui.common.JustSaved.pending.collect { saved ->
            if (saved == null) return@collect
            com.corriente.app.ui.common.JustSaved.pending.value = null
            scope.launch {
                val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                    snackbarState.showSnackbar(savedFormat.format(saved.text), undoLabel, duration = SnackbarDuration.Indefinite)
                }
                if (result == SnackbarResult.ActionPerformed) viewModel.deleteSilently(saved.txnId)
            }
        }
    }
    var showFilters by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val leftHanded by remember(context) { UiPrefs.leftHanded(context) }.collectAsState()
    val startCardDismissed by remember(context) { UiPrefs.startCardDismissed(context) }.collectAsState()

    // R3.3: экспорт списка операций (с наложенными на экране фильтрами) в CSV через SAF.
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsvTo { context.contentResolver.openOutputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
        }
    }

    // Тап по корректировке баланса: она не правится, но её можно рассмотреть и удалить.
    var adjustmentRow by remember { mutableStateOf<TxnRow?>(null) }
    adjustmentRow?.let { row ->
        AlertDialog(
            onDismissRequest = { adjustmentRow = null },
            title = { Text(stringResource(R.string.adjust_row_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.accountName} · ${row.amountText}", style = MaterialTheme.typography.bodyLarge)
                    row.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        stringResource(R.string.adjust_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { adjustmentRow = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteTxn(row.id); adjustmentRow = null }) {
                    Text(stringResource(R.string.txn_entry_delete))
                }
            },
        )
    }

    var overflowOpen by remember { mutableStateOf(false) }
    var incomeMenuOpen by remember { mutableStateOf(false) }
    // UI-2.2: кнопки «−»/«+» прячутся при прокрутке вниз, возвращаются при прокрутке вверх.
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        // Только пальцем: сдвиг списка из-за вставки шапки/пустого состояния (программный) кнопки не прячет.
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
        }.collect { (index, offset, byUser) ->
            val direction = if (byUser) scrollDirection(prevIndex, prevOffset, index, offset) else 0
            fabVisible = nextFabVisible(fabVisible, direction, atTop = index == 0 && offset == 0)
            prevIndex = index
            prevOffset = offset
        }
    }

    LaunchedEffect(listState) {
        com.corriente.app.ui.common.TabReselect.events.collect { route ->
            if (route == "transactions") listState.animateScrollToItem(0)
        }
    }

    // Поиск, фильтр и меню действий — в строке над списком (раньше жили в верхней панели, которой больше нет).
    val toolActions: @Composable () -> Unit = {
        IconButton(onClick = {
            if (searchOpen) viewModel.setQuery("")
            searchOpen = !searchOpen
        }) {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.txn_search_hint))
        }
        IconButton(onClick = { showFilters = true }) {
            BadgedBox(badge = { if (state.filter.isActive) Badge() }) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.txn_filters),
                )
            }
        }
        Box {
            IconButton(onClick = { overflowOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfer_title)) },
                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                    onClick = { overflowOpen = false; onAddTransfer() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.budgets_title)) },
                    leadingIcon = { Icon(Icons.Outlined.Savings, contentDescription = null) },
                    onClick = { overflowOpen = false; onOpenBudgets() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_title)) },
                    leadingIcon = { Icon(Icons.Outlined.EventRepeat, contentDescription = null) },
                    onClick = { overflowOpen = false; onOpenRecurring() },
                )
                DropdownMenuItem(
                    // U01: экспорт — ровно показанный список, не вся история.
                    text = {
                        Column {
                            Text(stringResource(R.string.txn_export_csv))
                            Text(
                                stringResource(R.string.txn_export_csv_scope),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                    onClick = { overflowOpen = false; exportCsvLauncher.launch("corriente-transactions.csv") },
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButtonPosition = if (leftHanded) FabPosition.Start else FabPosition.End,
        floatingActionButton = {
            // Правша: «+» доход слева, «−» расход справа (у thumb-зоны). Левша: у левого края, порядок «−», «+».
            // Расход — самый частый сценарий, основная кнопка; «+» вторичного цвета.
            // UI-2.2: «+» — малая кнопка; долгий тап открывает меню «Доход / Перевод».
            // U09: обе кнопки подписаны словами — значок «+»/«−» без подписи приходилось угадывать.
            val income = @Composable {
                Box {
                    Surface(
                        shape = FloatingActionButtonDefaults.shape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shadowElevation = 3.dp,
                        modifier = Modifier.heightIn(min = 48.dp).combinedClickable(
                            onClick = onAddIncome,
                            onLongClick = { incomeMenuOpen = true },
                            onLongClickLabel = stringResource(R.string.txn_add_income_more),
                            role = Role.Button,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(stringResource(R.string.txn_fab_income), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    DropdownMenu(expanded = incomeMenuOpen, onDismissRequest = { incomeMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.txn_add_income)) },
                            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            onClick = { incomeMenuOpen = false; onAddIncome() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_title)) },
                            leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                            onClick = { incomeMenuOpen = false; onAddTransfer() },
                        )
                    }
                }
            }
            val expenseLabel = stringResource(R.string.txn_add_expense)
            val expense = @Composable {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Outlined.Remove, contentDescription = null) },
                    text = { Text(stringResource(R.string.txn_fab_expense)) },
                    modifier = Modifier.semantics { contentDescription = expenseLabel },
                )
            }
            AnimatedVisibility(
                visible = fabVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (leftHanded) {
                        expense()
                        income()
                    } else {
                        income()
                        expense()
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen || state.filter.query.isNotBlank()) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    label = { Text(stringResource(R.string.txn_search_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            ActiveFilterChips(
                state = state,
                onRemoveAccount = { viewModel.setAccountFilter(null) },
                onRemoveCurrency = { viewModel.setCurrencyFilter(null) },
                onRemoveCategory = { viewModel.setCategoryFilter(null) },
                onRemovePeriod = { viewModel.setPeriod(null, null) },
                onRemoveAmount = viewModel::clearAmountFilter,
                onClearAll = viewModel::clearFilters,
            )

            // Шапка с кольцом — первым элементом списка: уезжает вверх при прокрутке, кнопки «−»/«+» остаются.
            // Пока идёт поиск, она не нужна — показываем только найденное.
            val header = overview.overview?.takeIf { state.filter.query.isBlank() }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (header != null) {
                    item(key = "overview") {
                        MonthOverviewHeader(
                            overview = header,
                            onShiftMonth = overviewViewModel::shiftMonth,
                            onSelectAccount = viewModel::setAccountFilter,
                            onSelectAllAccounts = { code ->
                                viewModel.setAccountFilter(null)
                                viewModel.rememberAllAccounts(code)
                                overviewViewModel.setAllAccounts(code)
                            },
                            onCategoryClick = { kind, id -> onAddInCategory(kind, id, header.accountId) },
                            onBalanceClick = { scope.launch { listState.animateScrollToItem(1) } },
                        )
                    }
                }
                // U05: список — вся история, а не только месяц шапки; это сказано прямо, и есть явный переход
                // к операциям выбранного месяца (обычный фильтр периода, снимается своим чипом).
                if (header != null) {
                    item(key = "history-scope") {
                        HistoryScopeRow(
                            month = header.month,
                            showScope = state.filter.from == null && state.filter.to == null,
                            onOnlyMonth = { viewModel.setPeriod(header.month.atDay(1), header.month.atEndOfMonth()) },
                            tools = toolActions,
                        )
                    }
                }
                // Пока идёт поиск, шапка скрыта — строка с инструментами остаётся, чтобы поиск можно было закрыть.
                if (header == null && state.filter.query.isNotBlank()) {
                    item(key = "search-tools") {
                        HistoryScopeRow(
                            month = java.time.YearMonth.now(),
                            showScope = false,
                            onOnlyMonth = {},
                            tools = toolActions,
                        )
                    }
                }
                // U11: новая пустая база — необязательный старт. Существующие пользователи (есть хоть один счёт)
                // его не видят; «Пропустить» убирает карточку насовсем на этом устройстве.
                if (state.noAccountsAtAll == true && state.empty && !startCardDismissed) {
                    item(key = "start") {
                        StartCard(
                            onCreateAccount = onCreateAccount,
                            onRestore = onOpenBackup,
                            onSkip = { UiPrefs.dismissStartCard(context) },
                        )
                    }
                }
                if (state.sections.isEmpty()) {
                    item(key = "empty") {
                        when (txnEmptyKind(sectionsEmpty = true, anyExist = !state.empty, filterActive = state.filter.isActive)) {
                            TxnEmptyKind.FILTERED -> EmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = stringResource(R.string.txn_empty_filter_title),
                                body = stringResource(R.string.txn_list_no_match),
                                actionLabel = stringResource(R.string.txn_filter_clear),
                                onAction = viewModel::clearFilters,
                            )
                            TxnEmptyKind.PERIOD_EMPTY -> EmptyState(
                                icon = Icons.Outlined.ReceiptLong,
                                title = stringResource(R.string.txn_empty_period_title),
                                body = stringResource(R.string.txn_empty_period_body),
                                actionLabel = stringResource(R.string.txn_empty_add),
                                onAction = onAddExpense,
                            )
                            else -> EmptyState(
                                icon = Icons.Outlined.ReceiptLong,
                                title = stringResource(R.string.txn_empty_title),
                                body = stringResource(R.string.txn_empty_body),
                                actionLabel = stringResource(R.string.txn_empty_add),
                                onAction = onAddExpense,
                            )
                        }
                    }
                }
                state.sections.forEach { section ->
                    stickyHeader(key = "h-${section.date}") { DayHeader(section) }
                    itemsIndexed(section.rows, key = { _, it -> it.id }) { index, row ->
                        val shape = groupItemShape(index, section.rows.size)
                        SwipeableTxnRow(
                            row = row,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 12.dp, vertical = 1.dp)
                                .clip(shape),
                            onClick = {
                                when {
                                    row.isTransfer -> onEditTransfer(row.id)
                                    row.isAdjustment -> adjustmentRow = row
                                    row.editable -> onEditTransaction(row.id)
                                }
                            },
                            onDelete = { viewModel.deleteTxn(row.id) },
                            onRepeat = {
                                onRepeatTransaction(row.id, if (row.kind == MoneyKind.INCOME) EntryKind.INCOME else EntryKind.EXPENSE)
                            },
                        )
                    }
                }
                if (state.canLoadEarlier) {
                    item(key = "load-earlier") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = viewModel::loadEarlier) {
                                Text(stringResource(R.string.txn_list_load_earlier))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        TxnFilterSheet(
            state = state,
            today = java.time.LocalDate.now(),
            onApply = viewModel::applyFilter,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun DayHeader(section: DaySection) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            com.corriente.app.ui.common.dayLabel(section.date),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Column(horizontalAlignment = Alignment.End) {
            section.totals.forEach {
                com.corriente.app.ui.common.MoneyText(
                    it,
                    com.corriente.app.ui.common.MoneyKind.BALANCE,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    deemphasizeFraction = true,
                )
            }
        }
    }
}

@Composable
private fun TxnRowItem(row: TxnRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val badgeColor = if (row.color != 0) {
            Color(row.color)
        } else {
            MaterialTheme.colorScheme.secondary
        }
        Box(
            Modifier.size(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (row.isAdjustment) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else if (!row.isTransfer) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(badgeColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CategoryGlyph(row.icon, row.title, badgeColor, size = 20.dp)
                }
            } else {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (row.accountColor != 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                Color(row.accountColor),
                                CircleShape,
                            ),
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (row.isAdjustment) stringResource(R.string.adjust_row_title) else row.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Перевод: «сумма → сумма» длинная, поэтому уходит под название и не обрезает имена счетов.
            if (row.isTransfer) {
                com.corriente.app.ui.common.MoneyText(
                    row.amountText,
                    row.kind,
                    style = MaterialTheme.typography.titleSmall,
                    deemphasizeFraction = true,
                )
            }
        }
        if (!row.isTransfer) {
            com.corriente.app.ui.common.MoneyText(
                row.amountText,
                row.kind,
                style = MaterialTheme.typography.titleMedium,
                deemphasizeFraction = true,
            )
        }
    }
}


private const val UNDO_WINDOW_MS = 5_000L

/** U11: понятные способы начать; ни один не обязателен. */
@Composable
private fun StartCard(onCreateAccount: () -> Unit, onRestore: () -> Unit, onSkip: () -> Unit) {
    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.start_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.start_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_create_account))
            }
            androidx.compose.material3.OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_restore))
            }
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.start_skip)) }
        }
    }
}

@Composable
private fun HistoryScopeRow(
    month: java.time.YearMonth,
    showScope: Boolean,
    onOnlyMonth: () -> Unit,
    tools: @Composable () -> Unit,
) {
    val months = androidx.compose.ui.res.stringArrayResource(R.array.month_names)
    val monthLabel = "${months[month.monthValue - 1]} ${month.year}"
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showScope) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.overview_history_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.overview_only_month, monthLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOnlyMonth).padding(vertical = 2.dp),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        tools()
    }
}

/**
 * UI-3.2: строка операции со свайпами — влево «удалить» (с отменой в снекбаре), вправо «повторить»
 * (только для расходов/доходов; перевод так не повторяется). Направления не зеркалятся режимом левши — это жест, не кнопка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTxnRow(
    row: TxnRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRepeat = row.editable && !row.isTransfer
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onRepeat(); false } // строка возвращается на место
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = canRepeat,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val deleting = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier.fillMaxSize().background(if (deleting) colors.errorContainer else colors.secondaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(
                    if (deleting) Icons.Outlined.Delete else Icons.Outlined.Repeat,
                    contentDescription = null,
                    tint = if (deleting) colors.onErrorContainer else colors.onSecondaryContainer,
                )
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { TxnRowItem(row, onClick = onClick) }
    }
}
