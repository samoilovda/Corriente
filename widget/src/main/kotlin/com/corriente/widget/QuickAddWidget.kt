package com.corriente.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Виджет быстрого ввода 2×1: только «−» и «+», без балансов. Снимок из DataStore не читает и
 * обновлений от приложения не ждёт — вёрстка статична. Нажатие открывает приложение с тем же
 * deep link'ом, что и ярлыки на иконке (`corriente://entry?kind=…`, R4.4), т.е. обычный экран
 * ввода операции, а не окно быстрого расхода по категории.
 *
 * «−» — под большим пальцем, как кнопки на главном экране приложения: у правшей справа («+», «−»), при
 * «Варианте для левшей» слева («−», «+»). На вытянутом по вертикали виджете кнопки встают столбиком
 * в том же порядке.
 */
class QuickAddWidget : GlanceAppWidget() {

    // Exact: вёрстка тривиальная, а ориентацию (ряд/столбик) решает фактическое соотношение сторон.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val leftHanded by remember { leftHandedFlow(context) }.collectAsState(initial = readLeftHanded(context))
            QuickAddBody(leftHanded)
        }
    }
}

// Glance рисует в процессе приложения — его SharedPreferences доступны. Файл и ключ — из UiPrefs (:app).
// Поток, а не разовое чтение: живой сеанс Glance при updateAll() не перезапускает provideGlance.
private fun readLeftHanded(context: Context) =
    context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getBoolean("left_handed", false)

private fun leftHandedFlow(context: Context) = callbackFlow {
    val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "left_handed") trySend(readLeftHanded(context))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    send(readLeftHanded(context))
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}

// Тона income/expense из CorrienteColors (:app, ui/theme/Color.kt) — тема приложения сюда не доступна.
private val ExpenseContainer = ColorProvider(day = Color(0xFFFFDAD6), night = Color(0xFF5C1D18))
private val OnExpenseContainer = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFFFB4AB))
private val IncomeContainer = ColorProvider(day = Color(0xFFC6EFD0), night = Color(0xFF1C4A2A))
private val OnIncomeContainer = ColorProvider(day = Color(0xFF1B6E3A), night = Color(0xFF7FD99A))

// Явный intent на MainActivity с data-URI — как в res/xml/shortcuts.xml :app. CLEAR_TASK — как у
// ярлыков: иначе NavController.handleDeepLink сам перезапустит Activity с этим флагом.
private fun entryIntent(appPackage: String, kind: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse("corriente://entry?kind=$kind")).apply {
        component = ComponentName(appPackage, "com.corriente.app.MainActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

@Composable
private fun QuickAddBody(leftHanded: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val modifier = GlanceModifier.fillMaxSize().padding(2.dp)
    val kinds = if (leftHanded) listOf("EXPENSE", "INCOME") else listOf("INCOME", "EXPENSE")
    if (size.height > size.width) {
        Column(modifier = modifier) {
            kinds.forEach { QuickAddButton(context, it, GlanceModifier.defaultWeight().fillMaxWidth()) }
        }
    } else {
        Row(modifier = modifier) {
            kinds.forEach { QuickAddButton(context, it, GlanceModifier.defaultWeight().fillMaxHeight()) }
        }
    }
}

@Composable
private fun QuickAddButton(context: Context, kind: String, slot: GlanceModifier) {
    val expense = kind == "EXPENSE"
    // Как в CorrienteWidget: внешний Box даёт реальный зазор между залитыми кнопками.
    // `slot` — вес плюс растяжение по поперечной оси; fillMaxSize() здесь перебил бы вес (в Glance
    // последний модификатор ширины/высоты выигрывает) и первая кнопка заняла бы весь виджет.
    Box(modifier = slot.padding(3.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(if (expense) ExpenseContainer else IncomeContainer)
                .cornerRadius(16.dp)
                .semantics {
                    contentDescription = context.getString(
                        if (expense) R.string.quick_add_expense else R.string.quick_add_income,
                    )
                }
                .clickable(actionStartActivity(entryIntent(context.packageName, kind))),
        ) {
            Text(
                text = if (expense) "−" else "+",
                style = TextStyle(
                    color = if (expense) OnExpenseContainer else OnIncomeContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
