package com.corriente.app.navigation

import com.corriente.app.ui.txnentry.EntryKind

/**
 * Запуск с виджета «−/+» (`corriente://entry?kind=…&from=widget`, см. `:widget`, `QuickAddWidget.entryIntent`):
 * какую форму открыть, или `null`, если intent не от виджета.
 *
 * Такой запуск не идёт через deep link: форма ввода становится стартовым экраном. Иначе на долю секунды
 * рисовались «Операции», а затем форма выезжала снизу анимацией перехода.
 */
fun widgetEntryKind(dataString: String?): EntryKind? {
    val prefix = "corriente://entry?"
    if (dataString == null || !dataString.startsWith(prefix)) return null
    val params = dataString.removePrefix(prefix).split('&')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap()
    if (params["from"] != "widget") return null
    // Неизвестный kind — расход, как и у deep link.
    return EntryKind.entries.firstOrNull { it.name == params["kind"] } ?: EntryKind.EXPENSE
}
