package com.corriente.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Настройки внешнего вида, относящиеся к конкретному устройству (как [AppLocale]): `SharedPreferences`,
 * не БД — в бэкап и в синхронизацию между устройствами не попадают.
 */
object UiPrefs {

    private const val PREFS = "ui_prefs"
    private const val KEY_LEFT_HANDED = "left_handed"

    private var leftHandedFlow: MutableStateFlow<Boolean>? = null

    /** «Вариант для левшей»: кнопки «−»/«+» на главном экране — у левого края, порядок «−», «+». */
    fun leftHanded(context: Context): StateFlow<Boolean> = flow(context)

    fun setLeftHanded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LEFT_HANDED, value).apply()
        flow(context).value = value
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun flow(context: Context): MutableStateFlow<Boolean> =
        leftHandedFlow ?: MutableStateFlow(prefs(context).getBoolean(KEY_LEFT_HANDED, false)).also { leftHandedFlow = it }
}
