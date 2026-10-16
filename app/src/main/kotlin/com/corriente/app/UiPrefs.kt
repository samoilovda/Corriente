package com.corriente.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** UI-1.5: тема оформления — по системе, светлая или тёмная. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        /** Неизвестное/пустое сохранённое значение → [SYSTEM] (настройка не должна ронять запуск). */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Настройки внешнего вида, относящиеся к конкретному устройству (как [AppLocale]): `SharedPreferences`,
 * не БД — в бэкап и в синхронизацию между устройствами не попадают.
 */
object UiPrefs {

    private const val PREFS = "ui_prefs"
    private const val KEY_LEFT_HANDED = "left_handed"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private var leftHandedFlow: MutableStateFlow<Boolean>? = null
    private var themeModeFlow: MutableStateFlow<ThemeMode>? = null
    private var dynamicColorFlow: MutableStateFlow<Boolean>? = null

    /** «Вариант для левшей»: кнопки «−»/«+» на главном экране — у левого края, порядок «−», «+». */
    fun leftHanded(context: Context): StateFlow<Boolean> = leftHandedFlow(context)

    fun setLeftHanded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LEFT_HANDED, value).apply()
        leftHandedFlow(context).value = value
    }

    /** UI-1.5: тема (системная/светлая/тёмная). */
    fun themeMode(context: Context): StateFlow<ThemeMode> = themeModeFlow(context)

    fun setThemeMode(context: Context, value: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, value.name).apply()
        themeModeFlow(context).value = value
    }

    /** UI-1.5: «Цвета из обоев» (Material You, Android 12+). По умолчанию включено, как и было. */
    fun dynamicColor(context: Context): StateFlow<Boolean> = dynamicColorFlow(context)

    fun setDynamicColor(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        dynamicColorFlow(context).value = value
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun leftHandedFlow(context: Context): MutableStateFlow<Boolean> =
        leftHandedFlow ?: MutableStateFlow(prefs(context).getBoolean(KEY_LEFT_HANDED, false)).also { leftHandedFlow = it }

    @Synchronized
    private fun themeModeFlow(context: Context): MutableStateFlow<ThemeMode> =
        themeModeFlow ?: MutableStateFlow(ThemeMode.fromStored(prefs(context).getString(KEY_THEME_MODE, null)))
            .also { themeModeFlow = it }

    @Synchronized
    private fun dynamicColorFlow(context: Context): MutableStateFlow<Boolean> =
        dynamicColorFlow ?: MutableStateFlow(prefs(context).getBoolean(KEY_DYNAMIC_COLOR, true)).also { dynamicColorFlow = it }
}
