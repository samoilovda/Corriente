package com.corriente.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.corriente.data.widget.WidgetSnapshot
import com.corriente.data.widget.WidgetSnapshotStore
import kotlinx.coroutines.flow.first

/**
 * T4.2: виджет на Glance. Рисуется в процессе лаунчера — читает только готовый
 * [WidgetSnapshot] из DataStore (ARCHITECTURE.md §4.2). До первой разблокировки телефона
 * (Direct Boot) хранилище недоступно — тогда показываем заглушку, а не падаем (§4.4 п.3).
 */
class CorrienteWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

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

private const val APP_PACKAGE = "com.corriente.app"

private fun quickExpenseIntent(categoryId: String, categoryName: String) =
    Intent().apply {
        component = ComponentName(APP_PACKAGE, "$APP_PACKAGE.quick.QuickExpenseActivity")
        putExtra("category_id", categoryId)
        putExtra("category_name", categoryName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private fun changeActiveAccountIntent() =
    Intent().apply {
        component = ComponentName(APP_PACKAGE, "$APP_PACKAGE.quick.ChangeActiveAccountActivity")
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

            else -> {
                if (snapshot.activeAccountName.isNotEmpty()) {
                    Text(
                        text = snapshot.activeAccountName,
                        style = captionStyle(),
                        modifier = GlanceModifier.clickable(actionStartActivity(changeActiveAccountIntent())),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                }
                snapshot.balances.forEach { line -> Text(line.formatted, style = balanceStyle()) }

                if (snapshot.monthExpenses.isNotEmpty()) {
                    Spacer(GlanceModifier.height(8.dp))
                    Text(context.getString(R.string.widget_month_expenses), style = captionStyle())
                    snapshot.monthExpenses.forEach { line -> Text(line.formatted, style = captionStyle()) }
                }

                if (snapshot.quickCategories.isNotEmpty()) {
                    Spacer(GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        snapshot.quickCategories.take(4).forEach { category ->
                            Text(
                                text = category.icon?.takeIf { it.isNotBlank() } ?: category.name.take(3),
                                style = captionStyle(),
                                modifier = GlanceModifier
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .clickable(
                                        actionStartActivity(quickExpenseIntent(category.id, category.name)),
                                    ),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                        }
                    }
                }
            }
        }
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
