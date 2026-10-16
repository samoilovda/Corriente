package com.corriente.app.ui.transactions

/**
 * UI-2.2: направление прокрутки списка по смещению первого видимого элемента:
 * `1` — вниз по списку (содержимое уезжает вверх), `-1` — вверх, `0` — не сдвинулся.
 * Только целые: индекс элемента и пиксельное смещение внутри него.
 */
fun scrollDirection(prevIndex: Int, prevOffset: Int, index: Int, offset: Int): Int = when {
    index > prevIndex -> 1
    index < prevIndex -> -1
    offset > prevOffset -> 1
    offset < prevOffset -> -1
    else -> 0
}

/** Кнопки «−»/«+» прячутся при прокрутке вниз и возвращаются при прокрутке вверх или у самого верха. */
fun nextFabVisible(visible: Boolean, direction: Int, atTop: Boolean): Boolean = when {
    atTop -> true
    direction > 0 -> false
    direction < 0 -> true
    else -> visible
}
