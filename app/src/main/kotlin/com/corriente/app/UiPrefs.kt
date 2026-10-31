package com.corriente.app

import android.content.Context
import com.corriente.data.quickentry.FavouriteCategories
import com.corriente.data.quickentry.FavouriteIdsCodec
import com.corriente.data.quickentry.FavouriteSettings
import com.corriente.data.quickentry.FrequentEntry
import com.corriente.data.quickentry.InMemoryFavouriteCategories
import com.corriente.data.quickentry.InMemoryPinnedEntries
import com.corriente.data.quickentry.PinnedEntries
import com.corriente.data.quickentry.PinnedEntryCodec
import com.corriente.data.usecase.InMemoryStartAccountSettings
import com.corriente.data.usecase.StartAccountMode
import com.corriente.data.usecase.StartAccountSetting
import com.corriente.data.usecase.StartAccountSettings
import com.corriente.data.usecase.StartSelection
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

    // Имя файла и ключ «для левшей» читает и виджет «−/+» (:widget, QuickAddWidget) — :app ему недоступен.
    private const val PREFS = "ui_prefs"
    private const val KEY_LEFT_HANDED = "left_handed"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_START_CARD_DISMISSED = "start_card_dismissed"
    private const val KEY_PINNED_ENTRIES = "pinned_entries"
    private const val KEY_FAVOURITES_ENABLED = "favourite_categories_enabled"
    private const val KEY_FAVOURITE_IDS = "favourite_category_ids"
    private const val KEY_START_ACCOUNT_MODE = "start_account_mode"
    private const val KEY_START_ACCOUNT_PINNED = "start_account_pinned"
    private const val KEY_START_LAST_ACCOUNT = "start_account_last_account"
    private const val KEY_START_LAST_CURRENCY = "start_account_last_currency"
    private const val KEY_START_LAST_CHOSEN = "start_account_last_chosen"
    private const val KEY_LAST_ACCOUNT = "last_entry_account"
    private const val KEY_KIND_LABELS_SEEN = "kind_labels_seen"

    private var leftHandedFlow: MutableStateFlow<Boolean>? = null
    private var themeModeFlow: MutableStateFlow<ThemeMode>? = null
    private var dynamicColorFlow: MutableStateFlow<Boolean>? = null
    private var startCardDismissedFlow: MutableStateFlow<Boolean>? = null
    private var pinnedEntries: PinnedEntries? = null
    private var favouriteCategories: FavouriteCategories? = null
    private var startAccountSettings: StartAccountSettings? = null

    /** Счёт последней записанной операции — форма ввода открывается с ним (и его валютой). */
    fun lastAccount(context: Context): com.corriente.app.ui.txnentry.LastAccountStore = object : com.corriente.app.ui.txnentry.LastAccountStore {
        private val prefs = prefs(context)
        override fun get(): String? = prefs.getString(KEY_LAST_ACCOUNT, null)
        override fun set(accountId: String) { prefs.edit().putString(KEY_LAST_ACCOUNT, accountId).apply() }
    }

    /** E1: закреплённые шаблоны быстрого ввода — на этом устройстве, вне БД, бэкапа и синхронизации. */
    @Synchronized
    fun pinnedEntries(context: Context): PinnedEntries =
        pinnedEntries ?: PrefsPinnedEntries(prefs(context)).also { pinnedEntries = it }

    /** Избранные категории экрана ввода (закреплённая строка) — на этом устройстве, вне БД, бэкапа и синхронизации. */
    @Synchronized
    fun favouriteCategories(context: Context): FavouriteCategories =
        favouriteCategories ?: PrefsFavouriteCategories(prefs(context)).also { favouriteCategories = it }

    /** Какой счёт открывать на «Операциях» при запуске — на этом устройстве, вне БД, бэкапа и синхронизации. */
    @Synchronized
    fun startAccountSettings(context: Context): StartAccountSettings =
        startAccountSettings ?: PrefsStartAccountSettings(prefs(context)).also { startAccountSettings = it }

    private class PrefsStartAccountSettings(private val prefs: android.content.SharedPreferences) : InMemoryStartAccountSettings(
        StartAccountSetting(
            mode = StartAccountMode.entries.firstOrNull { it.name == prefs.getString(KEY_START_ACCOUNT_MODE, null) }
                ?: StartAccountMode.LAST_TRANSACTION,
            pinnedAccountId = prefs.getString(KEY_START_ACCOUNT_PINNED, null),
            lastSelection = if (prefs.getBoolean(KEY_START_LAST_CHOSEN, false)) {
                StartSelection(prefs.getString(KEY_START_LAST_ACCOUNT, null), prefs.getString(KEY_START_LAST_CURRENCY, null))
            } else {
                null
            },
        ),
    ) {
        @Synchronized
        override fun update(transform: (StartAccountSetting) -> StartAccountSetting) {
            super.update(transform)
            val setting = state.value
            prefs.edit()
                .putString(KEY_START_ACCOUNT_MODE, setting.mode.name)
                .putString(KEY_START_ACCOUNT_PINNED, setting.pinnedAccountId)
                .putBoolean(KEY_START_LAST_CHOSEN, setting.lastSelection != null)
                .putString(KEY_START_LAST_ACCOUNT, setting.lastSelection?.accountId)
                .putString(KEY_START_LAST_CURRENCY, setting.lastSelection?.currency)
                .apply()
        }
    }

    private class PrefsFavouriteCategories(private val prefs: android.content.SharedPreferences) : InMemoryFavouriteCategories(
        FavouriteSettings(
            enabled = prefs.getBoolean(KEY_FAVOURITES_ENABLED, false),
            ids = FavouriteIdsCodec.decode(prefs.getString(KEY_FAVOURITE_IDS, null)),
        ),
    ) {
        private fun persist() {
            prefs.edit()
                .putBoolean(KEY_FAVOURITES_ENABLED, state.value.enabled)
                .putString(KEY_FAVOURITE_IDS, FavouriteIdsCodec.encode(state.value.ids))
                .apply()
        }

        @Synchronized
        override fun setEnabled(value: Boolean) { super.setEnabled(value); persist() }

        @Synchronized
        override fun add(id: String, sameKindIds: Set<String>): Boolean = super.add(id, sameKindIds).also { persist() }

        @Synchronized
        override fun remove(id: String) { super.remove(id); persist() }
    }

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

    /**
     * Подписи «Расход»/«Доход» на переключателе экрана ввода — только в первый запуск приложения на устройстве:
     * весь этот процесс показываем слова, со следующего запуска — компактные «−»/«+». Ответ кэшируется на процесс,
     * поэтому отметка в SharedPreferences не прячет подписи посреди первого же сеанса.
     */
    @Synchronized
    fun showKindLabels(context: Context): Boolean = showKindLabels ?: run {
        val prefs = prefs(context)
        val firstLaunch = !prefs.getBoolean(KEY_KIND_LABELS_SEEN, false)
        if (firstLaunch) prefs.edit().putBoolean(KEY_KIND_LABELS_SEEN, true).apply()
        firstLaunch.also { showKindLabels = it }
    }

    private var showKindLabels: Boolean? = null

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
