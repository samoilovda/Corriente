package com.corriente.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.corriente.data.widget.WidgetSnapshot
import com.corriente.data.widget.WidgetSnapshotStore
import kotlinx.coroutines.flow.first

/** R4.1: три раскладки под [SizeMode.Responsive] — компактная/средняя/крупная. */
private val SIZE_COMPACT = DpSize(180.dp, 110.dp)
private val SIZE_MEDIUM = DpSize(250.dp, 180.dp)
private val SIZE_LARGE = DpSize(250.dp, 280.dp)

/**
 * T4.2: виджет на Glance. Рисуется в процессе лаунчера — читает только готовый
 * [WidgetSnapshot] из DataStore (ARCHITECTURE.md §4.2). До первой разблокировки телефона
 * (Direct Boot) хранилище недоступно — тогда показываем заглушку, а не падаем (§4.4 п.3).
 *
 * R4.1: `SizeMode.Exact` с единственной вёрсткой обрезал строку из четырёх категорий на узком
 * виджете. `SizeMode.Responsive` с тремя фиксированными размерами — Glance сам подбирает
 * ближайший подходящий и рисует именно ту раскладку, под которую он посчитан.
 */
class CorrienteWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SIZE_COMPACT, SIZE_MEDIUM, SIZE_LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        val snapshot = if (!unlocked) {
            WidgetSnapshot.EMPTY
        } else {
            runCatching { WidgetSnapshotStore(context).snapshot.first() }.getOrDefault(WidgetSnapshot.EMPTY)
        }

        provideContent {
            GlanceTheme {
                WidgetBody(snapshot, locked = !unlocked)
            }
        }
    }
}

// F3.5: пакет берём у контекста — applicationIdSuffix в будущих типах сборки не сломает виджет.
private fun quickExpenseIntent(appPackage: String, categoryId: String, categoryName: String) =
    Intent().apply {
        component = ComponentName(appPackage, "com.corriente.app.quick.QuickExpenseActivity")
        putExtra("category_id", categoryId)
        putExtra("category_name", categoryName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private fun changeActiveAccountIntent(appPackage: String) =
    Intent().apply {
        component = ComponentName(appPackage, "com.corriente.app.quick.ChangeActiveAccountActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot, locked: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        when {
            locked -> Text(context.getString(R.string.widget_locked), style = captionStyle())

            snapshot.balances.isEmpty() && snapshot.monthExpenses.isEmpty() ->
                Text(context.getString(R.string.widget_empty), style = captionStyle())

            // R4.1: раскладка зависит от размера, который Glance подобрал из SizeMode.Responsive —
            // LocalSize.current гарантированно равен одному из объявленных SIZE_* (не произвольному).
            else -> when (LocalSize.current) {
                SIZE_LARGE -> LargeBody(snapshot, context)
                SIZE_MEDIUM -> MediumBody(snapshot, context)
                else -> CompactBody(snapshot, context)
            }
        }
    }
}

/** Компактная раскладка (~2×1): только балансы, читаемо на минимальном размере виджета. */
@Composable
private fun CompactBody(snapshot: WidgetSnapshot, context: Context) {
    AccountHeader(snapshot, context)
    snapshot.balances.forEach { line -> Text(line.formatted, style = balanceStyle()) }
}

/** Средняя раскладка (~4×2): балансы плюс расходы за месяц. */
@Composable
private fun MediumBody(snapshot: WidgetSnapshot, context: Context) {
    CompactBody(snapshot, context)
    if (snapshot.monthExpenses.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        Text(context.getString(R.string.widget_month_expenses), style = captionStyle())
        snapshot.monthExpenses.forEach { line -> Text(line.formatted, style = captionStyle()) }
    }
}

/**
 * Крупная раскладка (~4×4): плюс сетка частых категорий. По 3 в ряд (а не одним `Row` из 6,
 * как раньше на `SizeMode.Exact`) — не обрезается даже на этой, самой узкой из широких раскладок.
 */
@Composable
private fun LargeBody(snapshot: WidgetSnapshot, context: Context) {
    MediumBody(snapshot, context)
    if (snapshot.quickCategories.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        snapshot.quickCategories.chunked(3).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEach { category ->
                    Text(
                        text = category.icon?.takeIf { it.isNotBlank() } ?: category.name.take(3),
                        style = captionStyle(),
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clickable(
                                actionStartActivity(
                                    quickExpenseIntent(context.packageName, category.id, category.name),
                                ),
                            ),
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))
        }
    }
}

@Composable
private fun AccountHeader(snapshot: WidgetSnapshot, context: Context) {
    if (snapshot.activeAccountName.isNotEmpty()) {
        Text(
            text = snapshot.activeAccountName,
            style = captionStyle(),
            modifier = GlanceModifier.clickable(
                actionStartActivity(changeActiveAccountIntent(context.packageName)),
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
    }
}

@Composable
private fun balanceStyle() = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
)

@Composable
private fun captionStyle() = TextStyle(
    color = GlanceTheme.colors.onSurfaceVariant,
    fontSize = 12.sp,
)
