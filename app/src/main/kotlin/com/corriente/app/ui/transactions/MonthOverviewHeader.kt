package com.corriente.app.ui.transactions

import com.corriente.data.usecase.MonthOverview
import com.corriente.data.usecase.OverviewIcon
import com.corriente.data.usecase.OverviewSlice
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corriente.app.R
import com.corriente.app.ui.common.CategoryGlyph
import kotlin.math.cos
import kotlin.math.sin


/** Та же палитра-подстраховка, что у кольца в «Отчёте», для категорий без своего цвета. */
private val FALLBACK = listOf(
    0xFF4E79A7, 0xFFF28E2B, 0xFFE15759, 0xFF76B7B2, 0xFF59A14F,
    0xFFEDC948, 0xFFB07AA1, 0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AC,
).map { Color(it) }

private val RING_SIZE = 132.dp
private val ICON_SIZE = 34.dp
private val ORBIT_RADIUS = 112.dp
private val HEADER_SIZE = ORBIT_RADIUS * 2 + ICON_SIZE + 36.dp

/**
 * Шапка «Операций» в духе Monefy: выбор счёта и месяца, небольшое кольцо расходов с иконками категорий вокруг
 * (тап — ввод расхода в эту категорию), «Баланс» под кольцом. Живёт первым элементом списка и уезжает при прокрутке.
 */
@Composable
fun MonthOverviewHeader(
    overview: MonthOverview,
    onShiftMonth: (Long) -> Unit,
    onSelectAccount: (String) -> Unit,
    onSelectAllAccounts: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onBalanceClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountSelector(overview, onSelectAccount, onSelectAllAccounts, Modifier.weight(1f))
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.overview_prev_month))
            }
            val months = stringArrayResource(R.array.month_names)
            Text(
                "${months[overview.month.monthValue - 1]} ${overview.month.year}",
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = { onShiftMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.overview_next_month))
            }
        }

        Box(Modifier.size(HEADER_SIZE), contentAlignment = Alignment.Center) {
            Ring(overview.slices, Modifier.size(RING_SIZE))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(RING_SIZE - 44.dp)) {
                CenterAmount(overview.incomeText, com.corriente.app.ui.theme.LocalCorrienteColors.current.income)
                CenterAmount(overview.expenseText, com.corriente.app.ui.theme.LocalCorrienteColors.current.expense)
            }
            val n = overview.icons.size
            overview.icons.forEachIndexed { i, icon ->
                // Равномерно по окружности, начиная сверху и по часовой стрелке.
                val angle = Math.toRadians(-90.0 + 360.0 * i / n)
                CategoryIcon(
                    icon = icon,
                    fallback = FALLBACK[i % FALLBACK.size],
                    onClick = { onCategoryClick(icon.categoryId) },
                    modifier = Modifier.offset(
                        x = ORBIT_RADIUS * cos(angle).toFloat(),
                        y = ORBIT_RADIUS * sin(angle).toFloat() + 7.dp,
                    ),
                )
            }
        }

        Surface(
            onClick = onBalanceClick,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.overview_balance), style = MaterialTheme.typography.titleMedium)
                com.corriente.app.ui.common.MoneyText(
                    "  ${overview.balanceText}",
                    com.corriente.app.ui.common.MoneyKind.BALANCE,
                    isNegativeBalance = overview.balanceNegative,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (overview.balanceNegative) com.corriente.app.ui.theme.LocalCorrienteColors.current.expense else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun CenterAmount(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Ring(slices: List<OverviewSlice>, modifier: Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val uncategorized = MaterialTheme.colorScheme.outline
    // Float — только координаты отрисовки (I-1); доли считаются из уже посчитанных Long.
    val total = slices.sumOf { it.valueMinor }.toDouble()
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension / 6.5f)
        val inset = stroke.width / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        if (total <= 0.0) {
            drawArc(empty, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            return@Canvas
        }
        var start = -90f
        slices.forEachIndexed { i, slice ->
            val sweep = (slice.valueMinor / total * 360.0).toFloat()
            val color = when {
                slice.color != 0 -> Color(slice.color)
                else -> uncategorized
            }
            drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            start += sweep
        }
    }
}

@Composable
private fun CategoryIcon(icon: OverviewIcon, fallback: Color, onClick: () -> Unit, modifier: Modifier) {
    val color = if (icon.color != 0) Color(icon.color) else fallback
    Column(
        modifier
            .width(ICON_SIZE + 18.dp)
            .semantics { contentDescription = icon.name }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(ICON_SIZE).background(color.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CategoryGlyph(icon.icon, icon.name, color, size = 20.dp)
        }
        Text(
            icon.percent?.let { "$it%" } ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountSelector(
    overview: MonthOverview,
    onSelectAccount: (String) -> Unit,
    onSelectAllAccounts: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val allAccounts = stringResource(R.string.txn_list_all_accounts)
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text(
                overview.accountName ?: "$allAccounts · ${overview.currencyCode}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            overview.currencyOptions.forEach { code ->
                DropdownMenuItem(
                    text = { Text("$allAccounts · $code") },
                    onClick = { open = false; onSelectAllAccounts(code) },
                )
            }
            if (overview.accountOptions.isNotEmpty()) HorizontalDivider()
            overview.accountOptions.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.name} · ${account.currency.code}") },
                    onClick = { open = false; onSelectAccount(account.id) },
                )
            }
        }
    }
}
