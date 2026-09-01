package com.corriente.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Точка входа платформы. Приложение обновляет виджет через `CorrienteWidget().updateAll(context)`
 * после каждой записи в БД — на `updatePeriodMillis` полагаться нельзя (ARCHITECTURE.md §4.4).
 */
class CorrienteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CorrienteWidget()
}
