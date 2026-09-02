package com.corriente.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_config")

/**
 * Настройки виджета (T4.4): какие 1–3 валюты закреплены и в какой счёт пишет быстрый ввод.
 * R4.3: плюс до 6 вручную закреплённых категорий, в порядке показа в виджете.
 * Пустой конфиг = «ещё не настроено», тогда [WidgetUpdater] берёт значения по умолчанию
 * ([defaultPinnedCurrencies]/[defaultQuickCategories], первый счёт).
 */
data class WidgetConfig(
    val pinnedCurrencyCodes: List<String> = emptyList(),
    val activeAccountId: String? = null,
    val pinnedCategoryIds: List<String> = emptyList(),
)

class WidgetConfigStore(private val context: Context) {

    private val pinnedKey = stringPreferencesKey("pinned_currency_codes")
    private val activeKey = stringPreferencesKey("active_account_id")
    private val pinnedCategoriesKey = stringPreferencesKey("pinned_category_ids")

    val config: Flow<WidgetConfig> = context.widgetConfigDataStore.data.map { prefs ->
        WidgetConfig(
            pinnedCurrencyCodes = prefs[pinnedKey]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            activeAccountId = prefs[activeKey]?.takeIf { it.isNotBlank() },
            pinnedCategoryIds = prefs[pinnedCategoriesKey]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }

    suspend fun setPinnedCurrencies(codes: List<String>) {
        context.widgetConfigDataStore.edit { it[pinnedKey] = codes.take(MAX_PINNED_CURRENCIES).joinToString(",") }
    }

    suspend fun setActiveAccount(accountId: String) {
        context.widgetConfigDataStore.edit { it[activeKey] = accountId }
    }

    /** R4.3: закрепление до 6 категорий в порядке показа — ровно как [setPinnedCurrencies]. */
    suspend fun setPinnedCategories(categoryIds: List<String>) {
        context.widgetConfigDataStore.edit {
            it[pinnedCategoriesKey] = categoryIds.take(MAX_QUICK_CATEGORIES).joinToString(",")
        }
    }

    /** Сброс к значениям по умолчанию (F3.4): снять закреплённые валюты/категории и выбор счёта. */
    suspend fun reset() {
        context.widgetConfigDataStore.edit {
            it.remove(pinnedKey)
            it.remove(activeKey)
            it.remove(pinnedCategoriesKey)
        }
    }
}
