package com.corriente.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Точка входа платформы для [QuickAddIncomeWidget] — кнопка «+» 1×1. */
class QuickAddIncomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddIncomeWidget()
}

/** Точка входа платформы для [QuickAddExpenseWidget] — кнопка «−» 1×1. */
class QuickAddExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddExpenseWidget()
}
