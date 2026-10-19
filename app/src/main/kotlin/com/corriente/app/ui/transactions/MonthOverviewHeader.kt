package com.corriente.app.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import com.corriente.data.usecase.MonthOverview
import com.corriente.data.usecase.OverviewIcon
import com.corriente.data.usecase.OverviewSlice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corriente.app.R
import com.corriente.app.ui.txnentry.EntryKind
import com.corriente.app.ui.common.CategoryGlyph
import kotlin.math.atan2
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
 * (тап — ввод расхода в эту категорию). Живёт первым элементом списка и уезжает при прокрутке.
 *
 * U05/U08: суммы месяца подписаны «Расходы · месяц» / «Доходы · месяц» и показаны целиком под кольцом
 * (кольцо — дополнение, а не контейнер для обрезанных сумм); баланс подписан отдельно — он за все даты,
 * а не за выбранный месяц.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MonthOverviewHeader(
    overview: MonthOverview,
    onShiftMonth: (Long) -> Unit,
    onSelectAccount: (String) -> Unit,
    onSelectAllAccounts: (String) -> Unit,
    onCategoryClick: (EntryKind, String) -> Unit,
    onBalanceClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountSelector(overview, onSelectAccount, onSelectAllAccounts, Modifier.weight(1f))
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.overview_prev_month))
            }
            val months = stringArrayResource(R.array.month_names)
            // UI-3.1: смена месяца — сдвиг подписи по направлению (вперёд — влево, назад — вправо).
            AnimatedContent(
                targetState = overview.month,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { width -> if (forward) width / 2 else -width / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> if (forward) -width / 2 else width / 2 } + fadeOut())
                },
                label = "overview-month",
            ) { month ->
                Text(
                    "${months[month.monthValue - 1]} ${month.year}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = { onShiftMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.overview_next_month))
            }
        }

        // Тап по плитке «Расходы»/«Доходы» переключает кольцо; режим живёт, пока жив экран.
        var showIncome by rememberSaveable { mutableStateOf(false) }
        val slices = if (showIncome) overview.incomeSlices else overview.slices
        val icons = if (showIncome) overview.incomeIcons else overview.icons
        val months = stringArrayResource(R.array.month_names)
        val monthLabel = "${months[overview.month.monthValue - 1]} ${overview.month.year}"
        val ringDescription = stringResource(
            R.string.overview_ring_cd,
            monthLabel,
            icons.filter { it.percent != null }.joinToString { "${it.name} ${it.percent}%" }
                .ifEmpty { stringResource(R.string.overview_ring_empty) },
        )
        val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
        Box(
            Modifier.size(HEADER_SIZE).pointerInput(onShiftMonth) {
                // Свайп влево — следующий месяц, вправо — предыдущий (как листание страниц).
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { change, dx -> change.consume(); dragged += dx },
                    onDragEnd = {
                        if (dragged <= -swipeThreshold) onShiftMonth(1) else if (dragged >= swipeThreshold) onShiftMonth(-1)
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Ring(slices, icons, Modifier.size(RING_SIZE).clearAndSetSemantics { contentDescription = ringDescription })
            Column(
                modifier = Modifier.width(RING_SIZE - 36.dp).clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (showIncome) R.string.category_kind_income else R.string.category_kind_expense),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                com.corriente.app.ui.common.MoneyText(
                    if (showIncome) overview.incomeAmountText else overview.expenseAmountText,
                    if (showIncome) com.corriente.app.ui.common.MoneyKind.INCOME else com.corriente.app.ui.common.MoneyKind.EXPENSE,
                    style = MaterialTheme.typography.titleMedium,
                    deemphasizeFraction = true,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Text(
                    overview.currencySymbol,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val n = icons.size
            icons.forEachIndexed { i, icon ->
                // Равномерно по окружности, начиная сверху и по часовой стрелке.
                val angle = Math.toRadians(-90.0 + 360.0 * i / n)
                CategoryIcon(
                    icon = icon,
                    fallback = FALLBACK[i % FALLBACK.size],
                    onClick = { onCategoryClick(if (showIncome) EntryKind.INCOME else EntryKind.EXPENSE, icon.categoryId) },
                    modifier = Modifier.offset(
                        x = ORBIT_RADIUS * cos(angle).toFloat(),
                        y = ORBIT_RADIUS * sin(angle).toFloat() + 7.dp,
                    ),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthAmountCard(
                label = stringResource(R.string.overview_expenses_for, monthLabel),
                amount = overview.expenseAmountText,
                currency = overview.currencySymbol,
                description = overview.expenseText,
                kind = com.corriente.app.ui.common.MoneyKind.EXPENSE,
                selected = !showIncome,
                onClick = { showIncome = false },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MonthAmountCard(
                label = stringResource(R.string.overview_income_for, monthLabel),
                amount = overview.incomeAmountText,
                currency = overview.currencySymbol,
                description = overview.incomeText,
                kind = com.corriente.app.ui.common.MoneyKind.INCOME,
                selected = showIncome,
                onClick = { showIncome = true },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Surface(
            onClick = onBalanceClick,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.overview_balance),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                com.corriente.app.ui.common.MoneyText(
                    overview.balanceText,
                    com.corriente.app.ui.common.MoneyKind.BALANCE,
                    isNegativeBalance = overview.balanceNegative,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (overview.balanceNegative) com.corriente.app.ui.theme.LocalCorrienteColors.current.expense else MaterialTheme.colorScheme.onSecondaryContainer,
                    deemphasizeFraction = true,
                )
            }
        }
    }
}

/** Bento-плитка суммы месяца с текстовой подписью; тап выбирает, какое кольцо рисуется (расходы/доходы). */
@Composable
private fun MonthAmountCard(
    label: String,
    amount: String,
    currency: String,
    description: String,
    kind: com.corriente.app.ui.common.MoneyKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(12.dp).semantics(mergeDescendants = true) { contentDescription = "$label: $description" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            com.corriente.app.ui.common.MoneyText(
                amount,
                kind,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                deemphasizeFraction = true,
                maxLines = 2,
            )
            Text(
                currency,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Ring(slices: List<OverviewSlice>, icons: List<OverviewIcon>, modifier: Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val uncategorized = MaterialTheme.colorScheme.outline
    // Float — только координаты отрисовки (I-1); доли считаются из уже посчитанных Long.
    val fractions = com.corriente.app.ui.report.ChartGeometry.fractionsOfTotal(slices.map { it.valueMinor })
    // UI-3.1: дуги «вырастают» при каждом новом наборе долей (смена месяца/счёта).
    val reveal = remember(slices) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(durationMillis = 600)) }
    val activeCount = fractions.count { it > 0.0 }
    val gap = if (activeCount > 1) 4f else 0f

    Canvas(modifier) {
        val strokeWidth = size.minDimension / 6.5f
        val inset = strokeWidth / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val radius = (size.minDimension - strokeWidth) / 2f
        val capDegrees = Math.toDegrees(atan2((strokeWidth / 2f).toDouble(), radius.toDouble())).toFloat()

        drawArc(
            trackColor,
            0f,
            360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth * 0.65f),
        )

        if (fractions.all { it == 0.0 }) {
            return@Canvas
        }

        var start = -90f
        slices.forEachIndexed { i, slice ->
            val fraction = fractions[i]
            if (fraction > 0.0) {
                val fullSweep = (fraction * 360.0).toFloat() * reveal.value
                val iconIndex = icons.indexOfFirst { it.categoryId != null && it.categoryId == slice.categoryId }
                val color = when {
                    slice.color != 0 -> Color(slice.color)
                    // Цвет дуги совпадает с цветом иконки на орбите (там та же палитра по индексу).
                    iconIndex >= 0 -> FALLBACK[iconIndex % FALLBACK.size]
                    slice.categoryId != null -> FALLBACK[(icons.size + i) % FALLBACK.size]
                    else -> uncategorized
                }
                if (activeCount == 1) {
                    val cap = if (fullSweep < 359f) StrokeCap.Round else StrokeCap.Butt
                    drawArc(color, start, fullSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = cap))
                } else if (fullSweep > gap + capDegrees * 2f) {
                    drawArc(
                        color = color,
                        startAngle = start + gap / 2f + capDegrees,
                        sweepAngle = fullSweep - gap - capDegrees * 2f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                } else {
                    val buttSweep = (fullSweep - gap).coerceAtLeast(1.5f)
                    drawArc(
                        color = color,
                        startAngle = start + (fullSweep - buttSweep) / 2f,
                        sweepAngle = buttSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                }
                start += fullSweep
            }
        }
    }
}

/** Тап — ввод в эту категорию; долгое нажатие — всплывающее окно с названием и суммой за месяц. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryIcon(icon: OverviewIcon, fallback: Color, onClick: () -> Unit, modifier: Modifier) {
    val color = if (icon.color != 0) Color(icon.color) else fallback
    var showHint by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(3_000)
            showHint = false
        }
    }
    Column(
        modifier
            .width(ICON_SIZE + 18.dp)
            // Категории без трат за месяц приглушены, чтобы акцент оставался на тех, что в кольце.
            .alpha(if (icon.percent == null) 0.55f else 1f)
            .semantics { contentDescription = icon.amountText?.let { "${icon.name}: $it" } ?: icon.name }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showHint = true
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
            modifier = Modifier.size(ICON_SIZE + 4.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CategoryGlyph(icon.icon, icon.name, color, size = 20.dp)
                DropdownMenu(expanded = showHint, onDismissRequest = { showHint = false }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(icon.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            icon.amountText ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            icon.percent?.let { "$it%" } ?: " ",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
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
