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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import com.corriente.data.usecase.MonthOverview
import com.corriente.data.usecase.OverviewIcon
import com.corriente.data.usecase.OverviewSlice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.corriente.data.usecase.RingSegment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
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

/** Кольцо — овал по ширине экрана (не шире [MAX_RING_WIDTH]); высота — доля ширины. */
private val MAX_RING_WIDTH = 340.dp
private const val RING_ASPECT = 0.56f
/** Толщина полоски — доля меньшей стороны овала. */
private const val RING_STROKE_FRACTION = 1f / 7.5f

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
    /**
     * Нужно ли заново «выращивать» кольцо для этого ключа (вкладка, месяц, счёт, режим). По умолчанию — всегда;
     * экран передаёт память из ViewModel, чтобы возврат из формы и новая операция не проигрывали анимацию снова.
     */
    shouldRevealRing: (Any) -> Boolean = { true },
    /** Список операций стоит в самом начале: свайп вниз по кольцу его не прокручивает, и жест можно отдать «Расходы» ⇄ «Доходы». */
    isListAtTop: () -> Boolean = { false },
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
        // Показанное кольцо отстаёт от выбора на половину переворота: на ребре (90°) подменяем данные.
        var displayIncome by remember { mutableStateOf(showIncome) }
        val flip = remember { Animatable(0f) }
        LaunchedEffect(showIncome) {
            if (displayIncome == showIncome) return@LaunchedEffect
            flip.animateTo(90f, tween(durationMillis = 200, easing = FastOutLinearInEasing))
            displayIncome = showIncome
            flip.snapTo(-90f)
            flip.animateTo(0f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        val segments = if (displayIncome) overview.incomeSegments else overview.segments
        val months = stringArrayResource(R.array.month_names)
        val monthLabel = "${months[overview.month.monthValue - 1]} ${overview.month.year}"
        val segmentNames = segments.map { segmentName(it) }
        val ringDescription = stringResource(
            R.string.overview_ring_cd,
            monthLabel,
            segments.indices.joinToString { "${segmentNames[it]} ${segments[it].percent}%" }
                .ifEmpty { stringResource(R.string.overview_ring_empty) },
        )
        val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
        val kind = if (displayIncome) EntryKind.INCOME else EntryKind.EXPENSE
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).pointerInput(onShiftMonth) {
                // Горизонталь: влево — следующий месяц, вправо — предыдущий (как листание страниц).
                // Вниз — «Расходы» ⇄ «Доходы», но только пока список в самом начале: тогда ему вниз прокручиваться
                // некуда, и жест никому не мешает. Любой другой вертикальный жест не трогаем — он остаётся списку (V4).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var dx = 0f
                    var dy = 0f
                    var mode = RingGesture.UNDECIDED
                    while (mode != RingGesture.IGNORED) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) { mode = RingGesture.IGNORED; break }
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (mode == RingGesture.UNDECIDED) {
                            mode = when {
                                kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) -> RingGesture.HORIZONTAL
                                dy > slop && dy > kotlin.math.abs(dx) && isListAtTop() -> RingGesture.FLIP
                                kotlin.math.abs(dy) > slop -> RingGesture.IGNORED
                                else -> RingGesture.UNDECIDED
                            }
                        }
                        if (mode == RingGesture.HORIZONTAL || mode == RingGesture.FLIP) change.consume()
                        if (!change.pressed) break
                    }
                    when (mode) {
                        RingGesture.HORIZONTAL ->
                            if (dx <= -swipeThreshold) onShiftMonth(1) else if (dx >= swipeThreshold) onShiftMonth(-1)
                        RingGesture.FLIP -> if (dy >= swipeThreshold) showIncome = !showIncome
                        else -> Unit
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            val ringWidth = (maxWidth - 32.dp).coerceAtMost(MAX_RING_WIDTH)
            val ringHeight = ringWidth * RING_ASPECT
            Box(
                Modifier.graphicsLayer {
                    rotationX = flip.value
                    cameraDistance = 14f * density
                },
                contentAlignment = Alignment.Center,
            ) {
            Ring(
                segments = segments,
                revealKey = listOf(com.corriente.app.ui.common.TabEnter.version, overview.month, overview.accountId, overview.currencyCode, displayIncome),
                shouldReveal = shouldRevealRing,
                onAdd = { id -> onCategoryClick(kind, id) },
                modifier = Modifier.size(ringWidth, ringHeight).clearAndSetSemantics { contentDescription = ringDescription },
            )
            Column(
                modifier = Modifier.width(ringWidth * 0.5f).clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (displayIncome) R.string.category_kind_income else R.string.category_kind_expense),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                com.corriente.app.ui.common.MoneyText(
                    if (displayIncome) overview.incomeAmountText else overview.expenseAmountText,
                    if (displayIncome) com.corriente.app.ui.common.MoneyKind.INCOME else com.corriente.app.ui.common.MoneyKind.EXPENSE,
                    style = MaterialTheme.typography.titleLarge,
                    deemphasizeFraction = true,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Text(
                    overview.currencySymbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
private fun segmentName(segment: RingSegment): String = when {
    segment.isOther -> stringResource(R.string.overview_other)
    else -> segment.name ?: stringResource(R.string.report_no_category)
}

/**
 * Кольцо-овал из полосок по категориям. Тап по полоске — всплывающее окно с названием и суммой (по «Прочее» — со
 * списком вошедших категорий); тап по строке окна — ввод в эту категорию. Остальные полоски при этом приглушаются.
 */
@Composable
private fun Ring(
    segments: List<RingSegment>,
    revealKey: Any,
    shouldReveal: (Any) -> Boolean,
    onAdd: (String) -> Unit,
    modifier: Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val neutral = MaterialTheme.colorScheme.outline
    // Float — только координаты отрисовки (I-1); доли считаются из уже посчитанных Long.
    val fractions = com.corriente.app.ui.report.ChartGeometry.fractionsOfTotal(segments.map { it.valueMinor })
    // UI-3.1: дуги «вырастают» при старте, при переходе с другой вкладки и при смене месяца/счёта/режима — но не при
    // возврате из формы ввода и не когда просто добавилась операция (ключ тот же, кольцо уже показано).
    val reveal = remember(revealKey) { Animatable(if (shouldReveal(revealKey)) 0f else 1f) }
    LaunchedEffect(reveal) { if (reveal.value < 1f) reveal.animateTo(1f, tween(durationMillis = 600)) }
    var selected by remember(segments) { mutableStateOf<Int?>(null) }
    val activeCount = fractions.count { it > 0.0 }
    val separator = MaterialTheme.colorScheme.background
    fun colorOf(i: Int): Color {
        val seg = segments[i]
        return when {
            seg.isOther -> neutral
            seg.color != 0 -> Color(seg.color)
            seg.categoryId != null -> FALLBACK[i % FALLBACK.size]
            else -> neutral
        }
    }
    // Начало и размах каждой полоски в градусах (по часовой, с -90°) — общие для рисования и для попадания тапом.
    val starts = remember(fractions) {
        var acc = -90.0
        fractions.map { f -> acc.also { acc += f * 360.0 } }
    }

    BoxWithConstraints(modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val strokePx = minOf(widthPx, heightPx) * RING_STROKE_FRACTION
        Canvas(
            Modifier.fillMaxSize().pointerInput(segments, fractions) {
                detectTapGestures { tap ->
                    // Эллипс: угол параметрический (как у drawArc на овале), попадание — в полосу кольца.
                    val rx = (widthPx - strokePx) / 2f
                    val ry = (heightPx - strokePx) / 2f
                    val nx = (tap.x - widthPx / 2f) / rx
                    val ny = (tap.y - heightPx / 2f) / ry
                    val dist = kotlin.math.hypot(nx, ny)
                    val half = strokePx / 2f / minOf(rx, ry)
                    if (dist < 1f - half * 1.1f || dist > 1f + half * 1.1f) { selected = null; return@detectTapGestures }
                    var deg = Math.toDegrees(atan2(ny.toDouble(), nx.toDouble()))
                    if (deg < -90.0) deg += 360.0
                    val hit = fractions.indices.firstOrNull { i ->
                        fractions[i] > 0.0 && deg >= starts[i] && deg < starts[i] + fractions[i] * 360.0
                    }
                    selected = if (hit == selected) null else hit
                }
            },
        ) {
            val inset = strokePx / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx))
            if (fractions.all { it == 0.0 }) return@Canvas

            segments.forEachIndexed { i, _ ->
                val fraction = fractions[i]
                if (fraction <= 0.0) return@forEachIndexed
                val color = colorOf(i).let { if (selected != null && selected != i) it.copy(alpha = 0.35f) else it }
                drawArc(
                    color = color,
                    startAngle = starts[i].toFloat(),
                    sweepAngle = (fraction * 360.0).toFloat() * reveal.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
            }

            // Разделители между полосками — ровные по толщине вдоль всего овала (угол на овале растягивает зазор).
            if (activeCount > 1) {
                val rx = arcSize.width / 2f
                val ry = arcSize.height / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val half = strokePx / 2f + 1f
                fractions.indices.filter { fractions[it] > 0.0 }.forEach { i ->
                    val t = Math.toRadians(starts[i])
                    val px = cx + rx * cos(t).toFloat()
                    val py = cy + ry * sin(t).toFloat()
                    // Нормаль к параметрическому эллипсу: (ry·cos t, rx·sin t).
                    val nx = ry * cos(t).toFloat()
                    val ny = rx * sin(t).toFloat()
                    val len = kotlin.math.hypot(nx, ny)
                    drawLine(
                        color = separator,
                        start = androidx.compose.ui.geometry.Offset(px - nx / len * half, py - ny / len * half),
                        end = androidx.compose.ui.geometry.Offset(px + nx / len * half, py + ny / len * half),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }
        }

        selected?.takeIf { it in segments.indices }?.let { index ->
            val segment = segments[index]
            val mid = Math.toRadians(starts[index] + fractions[index] * 180.0)
            val rx = (widthPx - strokePx) / 2f
            val ry = (heightPx - strokePx) / 2f
            Box(
                Modifier.offset {
                    androidx.compose.ui.unit.IntOffset(
                        (widthPx / 2f + rx * cos(mid).toFloat()).toInt(),
                        (heightPx / 2f + ry * sin(mid).toFloat()).toInt(),
                    )
                }.size(1.dp),
            ) {
                DropdownMenu(expanded = true, onDismissRequest = { selected = null }) {
                    SegmentDetails(segment, segmentName(segment), onAdd = { id -> selected = null; onAdd(id) })
                }
            }
        }
    }
}

@Composable
private fun SegmentDetails(segment: RingSegment, name: String, onAdd: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 160.dp)) {
        val rowModifier = Modifier.fillMaxWidth()
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${segment.percent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(segment.amountText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (segment.isOther) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            segment.members.forEach { member ->
                val id = member.categoryId
                Row(
                    rowModifier
                        .then(if (id != null) Modifier.clickable { onAdd(id) } else Modifier)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        member.name ?: stringResource(R.string.report_no_category),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(member.amountText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (segment.categoryId != null) {
            TextButton(onClick = { onAdd(segment.categoryId!!) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.overview_add_in_category))
            }
        }
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

/** Что делает жест по кольцу: решается после выхода за touch slop. */
private enum class RingGesture { UNDECIDED, HORIZONTAL, FLIP, IGNORED }
