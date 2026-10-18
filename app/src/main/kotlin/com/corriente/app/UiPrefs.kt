package com.corriente.app

import android.content.Context
import com.corriente.data.quickentry.FrequentEntry
import com.corriente.data.quickentry.InMemoryPinnedEntries
import com.corriente.data.quickentry.PinnedEntries
import com.corriente.data.quickentry.PinnedEntryCodec
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
    private const val KEY_START_CARD_DISMISSED = "start_card_dismissed"
    private const val KEY_PINNED_ENTRIES = "pinned_entries"

    private var leftHandedFlow: MutableStateFlow<Boolean>? = null
    private var themeModeFlow: MutableStateFlow<ThemeMode>? = null
    private var dynamicColorFlow: MutableStateFlow<Boolean>? = null
    private var startCardDismissedFlow: MutableStateFlow<Boolean>? = null
    private var pinnedEntries: PinnedEntries? = null

    /** E1: закреплённые шаблоны быстрого ввода — на этом устройстве, вне БД, бэкапа и синхронизации. */
    @Synchronized
    fun pinnedEntries(context: Context): PinnedEntries =
        pinnedEntries ?: PrefsPinnedEntries(prefs(context)).also { pinnedEntries = it }

    private class PrefsPinnedEntries(private val prefs: android.content.SharedPreferences) : PinnedEntries {
        private val delegate = InMemoryPinnedEntries(
            prefs.getString(KEY_PINNED_ENTRIES, null).orEmpty().lines().mapNotNull(PinnedEntryCodec::decode),
        )
        override val pinned = delegate.pinned

        @Synchronized
        override fun toggle(entry: FrequentEntry): Boolean {
            val accepted = delegate.toggle(entry)
            prefs.edit().putString(KEY_PINNED_ENTRIES, pinned.value.mapNotNull(PinnedEntryCodec::encode).joinToString("\n")).apply()
            return accepted
        }
    }

    /** U11: стартовая карточка пустой базы скрыта «Пропустить» — на этом устройстве больше не показывается. */
    fun startCardDismissed(context: Context): StateFlow<Boolean> = startCardDismissedFlow(context)

    fun dismissStartCard(context: Context) {
        prefs(context).edit().putBoolean(KEY_START_CARD_DISMISSED, true).apply()
        startCardDismissedFlow(context).value = true
    }

    /** Тестам: вернуть карточку (значение кэшируется в памяти процесса, правки SharedPreferences мало). */
    @androidx.annotation.VisibleForTesting
    fun resetStartCard(context: Context) {
        prefs(context).edit().remove(KEY_START_CARD_DISMISSED).commit()
        startCardDismissedFlow(context).value = false
    }

    @Synchronized
    private fun startCardDismissedFlow(context: Context): MutableStateFlow<Boolean> =
        startCardDismissedFlow ?: MutableStateFlow(prefs(context).getBoolean(KEY_START_CARD_DISMISSED, false))
            .also { startCardDismissedFlow = it }

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
