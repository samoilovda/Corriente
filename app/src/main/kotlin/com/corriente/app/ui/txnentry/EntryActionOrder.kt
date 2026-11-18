package com.corriente.app.ui.txnentry

/** Две кнопки под калькулятором формы ввода. */
enum class EntryAction { SAVE, CATEGORY }

/**
 * Порядок кнопок слева направо. Выбор категории сразу записывает операцию, поэтому «Категория» — под большим
 * пальцем: у правши справа («Добавить расход», «Категория»), в режиме для левшей — слева («Категория», «Добавить …»).
 */
fun entryActionOrder(leftHanded: Boolean): List<EntryAction> =
    if (leftHanded) listOf(EntryAction.CATEGORY, EntryAction.SAVE) else listOf(EntryAction.SAVE, EntryAction.CATEGORY)
