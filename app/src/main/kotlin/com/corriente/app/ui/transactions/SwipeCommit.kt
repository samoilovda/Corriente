package com.corriente.app.ui.transactions

import kotlin.math.abs

/** Сколько процентов ширины строки нужно протянуть, чтобы свайп удалил или повторил операцию. */
const val SWIPE_COMMIT_PERCENT = 50

/**
 * Свайп по строке операции срабатывает, только если строку протянули на [SWIPE_COMMIT_PERCENT] % её ширины.
 * Быстрый короткий взмах (по умолчанию SwipeToDismissBox удаляет по скорости при любом сдвиге) строку не трогает:
 * так случайно удалялись операции при прокрутке списка. Только целые пиксели.
 */
fun swipeCommits(offsetPx: Int, widthPx: Int): Boolean =
    widthPx > 0 && abs(offsetPx) * 100 >= widthPx * SWIPE_COMMIT_PERCENT
