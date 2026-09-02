package com.corriente.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.corriente.app.CorrienteApplication

/**
 * R4.0: `AppWidgetManager` шлёт ACTION_APPWIDGET_ENABLED/UPDATE явным intent'ом прямо на
 * зарегистрированного провайдера ([com.corriente.widget.CorrienteWidgetReceiver] в `:widget`) —
 * получить их напрямую здесь, в `:app`, нельзя (`:widget` не может зависеть от `:app`, чтобы
 * дёрнуть [com.corriente.app.widget.WidgetUpdater] оттуда). Поэтому `CorrienteWidgetReceiver`
 * при этих действиях ретранслирует внутренний широковещательный intent на пакет приложения,
 * а этот receiver его ловит и форсирует пере-проверку размещения.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? CorrienteApplication)?.widgetUpdater?.notifyPlacementChanged()
    }

    companion object {
        const val ACTION_WIDGET_PLACEMENT_CHANGED = "com.corriente.app.action.WIDGET_PLACEMENT_CHANGED"
    }
}
