package com.corriente.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Язык интерфейса, выбранный пользователем в «Настройках». Пусто — язык системы (как раньше). Хранится в
 * `SharedPreferences` (читается синхронно в `attachBaseContext`, до создания UI), а не в БД, и в бэкап не попадает.
 * Применяется обёрткой контекста ([wrap]) у [CorrienteApplication] и Activity — строки ресурсов, форматирование
 * и `getString` воркеров идут через тот же язык. Виджет на рабочем столе рисуется в процессе лаунчера и остаётся
 * на языке системы.
 */
object AppLocale {

    /** Значения для экрана выбора: `""` — как в системе, иначе BCP-47 тег из поддерживаемых. */
    val supported: List<String> = listOf("", "en", "ru")

    private const val PREFS = "app_locale"
    private const val KEY = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .takeIf { it in supported } ?: ""

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Контекст с выбранным языком; при «как в системе» возвращает [base] без изменений. */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
