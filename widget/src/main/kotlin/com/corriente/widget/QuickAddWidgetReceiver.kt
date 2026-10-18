package com.corriente.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Точка входа платформы для [QuickAddWidget]. В отличие от [CorrienteWidgetReceiver] ничего не
 * ретранслирует в `:app` — виджет не показывает данных, пересчитывать снимок ему не нужно.
 */
class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
