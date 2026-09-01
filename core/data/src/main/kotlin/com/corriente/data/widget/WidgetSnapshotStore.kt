package com.corriente.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_state")

/**
 * Единственный носитель снимка виджета между процессом приложения и процессом лаунчера
 * (ARCHITECTURE.md §4.2). Хранится как JSON-строка — снимок уже содержит только строки и
 * примитивы (I-1), схема плоская, отдельная таблица тут избыточна.
 */
class WidgetSnapshotStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("snapshot_json")

    val snapshot: Flow<WidgetSnapshot> = context.widgetDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull() }
            ?: WidgetSnapshot.EMPTY
    }

    suspend fun save(snapshot: WidgetSnapshot) {
        context.widgetDataStore.edit { it[key] = json.encodeToString(WidgetSnapshot.serializer(), snapshot) }
    }
}
