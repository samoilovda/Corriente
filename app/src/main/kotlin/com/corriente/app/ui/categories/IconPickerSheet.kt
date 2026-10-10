package com.corriente.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.corriente.app.R
import com.corriente.app.ui.common.CATEGORY_EMOJI
import com.corriente.app.ui.common.CategoryGlyph
import com.corriente.app.ui.common.CategoryIconCatalog
import com.corriente.data.category.materialIconKey
import com.corriente.data.category.materialIconRef

/**
 * Выбор иконки категории: вкладка «Иконки» (линейные, окрашены цветом категории, как в Monefy) и «Эмодзи»
 * (готовая сетка + поле для своего). [onPick] получает строку для `category.icon`; пустая строка — без иконки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    current: String,
    color: Int,
    name: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tint = if (color != 0) Color(color) else MaterialTheme.colorScheme.primary
    var tab by rememberSaveable { mutableIntStateOf(if (current.isNotBlank() && materialIconKey(current) == null) 1 else 0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.icon_tab_icons)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.icon_tab_emoji)) })
            }
            if (tab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryIconCatalog.sections.forEach { section ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "title-${section.titleRes}") {
                            Text(
                                stringResource(section.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(section.keys, key = { it }) { key ->
                            val ref = materialIconRef(key)
                            PickCell(selected = current == ref, tint = tint, description = key, onClick = { onPick(ref) }) {
                                CategoryGlyph(ref, name, tint, size = 24.dp)
                            }
                        }
                    }
                }
            } else {
                var custom by rememberSaveable { mutableStateOf(if (materialIconKey(current) == null) current else "") }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.take(8) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.icon_custom_emoji)) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) {
                        Text(stringResource(R.string.icon_use))
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(CATEGORY_EMOJI, key = { it }) { emoji ->
                        PickCell(selected = current == emoji, tint = tint, description = emoji, onClick = { onPick(emoji) }) {
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            TextButton(onClick = { onPick("") }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.icon_none))
            }
        }
    }
}

@Composable
private fun PickCell(
    selected: Boolean,
    tint: Color,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .background(tint.copy(alpha = if (selected) 0.28f else 0.08f), CircleShape)
            .then(if (selected) Modifier.border(2.dp, tint, CircleShape) else Modifier)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
