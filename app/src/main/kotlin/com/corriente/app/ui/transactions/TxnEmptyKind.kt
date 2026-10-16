package com.corriente.app.ui.transactions

/** UI-2.3: чем объяснять пустой список операций. */
enum class TxnEmptyKind {
    /** Список не пуст — ничего показывать не нужно. */
    NONE,

    /** В базе вообще нет операций. */
    NO_DATA,

    /** Операции есть, но фильтр/поиск ничего не оставил. */
    FILTERED,

    /** Фильтра нет, операции есть, но не в подгруженном окне (более ранние — по кнопке «Показать раньше»). */
    PERIOD_EMPTY,
}

fun txnEmptyKind(sectionsEmpty: Boolean, anyExist: Boolean, filterActive: Boolean): TxnEmptyKind = when {
    !sectionsEmpty -> TxnEmptyKind.NONE
    !anyExist -> TxnEmptyKind.NO_DATA
    filterActive -> TxnEmptyKind.FILTERED
    else -> TxnEmptyKind.PERIOD_EMPTY
}
