package com.corriente.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Точка входа платформы. Приложение обновляет виджет через `CorrienteWidget().updateAll(context)`
 * после каждой записи в БД — на `updatePeriodMillis` полагаться нельзя (ARCHITECTURE.md §4.4).
 *
 * R4.0: ACTION_APPWIDGET_ENABLED/UPDATE приходят системой явным intent'ом прямо сюда, минуя
 * любой другой receiver — а пересчитать снимок умеет только `WidgetUpdater` в `:app` (`:widget`
 * от `:app` не зависит, вызвать его напрямую нельзя). Поэтому ретранслируем внутренний
 * broadcast на свой же пакет — его ловит `WidgetRefreshReceiver` в `:app`.
 */
class CorrienteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CorrienteWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_ENABLED ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            context.sendBroadcast(Intent(ACTION_WIDGET_PLACEMENT_CHANGED).setPackage(context.packageName))
        }
    }

    private companion object {
        const val ACTION_WIDGET_PLACEMENT_CHANGED = "com.corriente.app.action.WIDGET_PLACEMENT_CHANGED"
    }
}
