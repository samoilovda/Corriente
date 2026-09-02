package com.corriente.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(
        factory = CategoriesViewModel.factory(corrienteContainer().categoryRepository),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val merge by viewModel.merge.collectAsState()
    val message by viewModel.messages.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    var addMenuOpen by remember { mutableStateOf(false) }

    val expensesTitle = stringResource(R.string.categories_expenses)
    val incomesTitle = stringResource(R.string.categories_incomes)
    val archivedTitle = stringResource(R.string.categories_archived)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_add))
                }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_kind_expense)) },
                        onClick = { addMenuOpen = false; viewModel.startCreate(CategoryKind.EXPENSE) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_kind_income)) },
                        onClick = { addMenuOpen = false; viewModel.startCreate(CategoryKind.INCOME) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            branch(expensesTitle, state.expense, viewModel::startEdit)
            branch(incomesTitle, state.income, viewModel::startEdit)
            if (state.archived.isNotEmpty()) {
                item { SectionHeader(archivedTitle) }
                items(state.archived, key = { "arch-${it.id}" }) { category ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category.name, Modifier.weight(1f))
                        IconButton(onClick = { viewModel.unarchive(category.id) }) {
                            Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.categories_unarchive))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    editor?.let { current ->
        CategoryEditorDialog(
            editor = current,
            parentOptions = parentOptionsFor(current, state),
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onArchive = { current.editingId?.let(viewModel::archive); viewModel.closeEditor() },
            onDelete = { current.editingId?.let(viewModel::deleteIfUnused); viewModel.closeEditor() },
            onMerge = { current.editingId?.let { id -> mergeSourceOf(id, state)?.let(viewModel::startMerge) } },
        )
    }

    merge?.let { request ->
        MergeDialog(request = request, onDismiss = viewModel::cancelMerge, onConfirm = viewModel::confirmMerge)
    }
}

private fun LazyListScope.branch(
    title: String,
    nodes: List<CategoryNode>,
    onEdit: (Category) -> Unit,
) {
    if (nodes.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title) }
    nodes.forEach { node ->
        item(key = node.category.id) { CategoryRow(node.category, indent = false, onClick = { onEdit(node.category) }) }
        items(node.children, key = { it.id }) { child ->
            CategoryRow(child, indent = true, onClick = { onEdit(child) })
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
}

@Composable
private fun CategoryRow(category: Category, indent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 40.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(16.dp).background(Color(category.color), CircleShape))
        Text(category.name, Modifier.weight(1f))
        category.icon?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
    HorizontalDivider()
}

@Composable
private fun CategoryEditorDialog(
    editor: CategoryEditor,
    parentOptions: List<Category>,
    onDismiss: () -> Unit,
    onSave: (CategoryForm) -> Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
) {
    var name by rememberSaveable(editor) { mutableStateOf(editor.name) }
    var parentId by rememberSaveable(editor) { mutableStateOf(editor.parentId) }
    var color by rememberSaveable(editor) { mutableIntStateOf(editor.color) }
    var icon by rememberSaveable(editor) { mutableStateOf(editor.icon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.editingId == null) R.string.categories_new else R.string.categories_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text(stringResource(R.string.categories_icon)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.categories_color), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryPalette.forEach { swatch ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(swatch), CircleShape)
                                .border(
                                    width = if (swatch == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = swatch },
                        )
                    }
                }
                if (!editor.hasChildren) {
                    Text(stringResource(R.string.categories_parent), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = parentId == null,
                            onClick = { parentId = null },
                            label = { Text(stringResource(R.string.categories_no_parent)) },
                        )
                        parentOptions.forEach { option ->
                            FilterChip(
                                selected = parentId == option.id,
                                onClick = { parentId = option.id },
                                label = { Text(option.name) },
                            )
                        }
                    }
                }
                if (editor.editingId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onArchive) { Text(stringResource(R.string.categories_archive)) }
                        if (!editor.hasChildren) {
                            TextButton(onClick = onMerge) { Text(stringResource(R.string.categories_merge)) }
                        }
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.categories_delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        CategoryForm(
                            name = name,
                            kind = editor.kind,
                            parentId = if (editor.hasChildren) null else parentId,
                            color = color,
                            icon = icon,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MergeDialog(request: MergeRequest, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_merge_title, request.from.name)) },
        text = {
            Column {
                Text(stringResource(R.string.categories_merge_hint, request.from.name))
                request.candidates.forEach { candidate ->
                    Text(
                        candidate.name,
                        Modifier.fillMaxWidth().clickable { onConfirm(candidate.id) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun parentOptionsFor(editor: CategoryEditor, state: CategoriesUiState): List<Category> {
    val branches = if (editor.kind == CategoryKind.EXPENSE) state.expense else state.income
    return branches.map { it.category }.filter { it.id != editor.editingId }
}

private fun mergeSourceOf(id: String, state: CategoriesUiState): Category? =
    (state.expense + state.income).flatMap { listOf(it.category) + it.children }.firstOrNull { it.id == id }
