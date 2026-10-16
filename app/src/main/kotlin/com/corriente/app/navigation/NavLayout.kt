package com.corriente.app.navigation

/** UI-3.3: как навигация раскладывается по ширине окна. */
enum class NavLayout {
    /** Телефон: нижняя панель. */
    BOTTOM_BAR,

    /** Узкий планшет / ландшафт: рейка слева вместо нижней панели. */
    RAIL,

    /** Планшет, складной, окно компьютера: рейка слева, «Операции» — список и ввод/детали в две панели. */
    RAIL_TWO_PANE,
}

/** Пороги — компактный/средний/расширенный классы ширины Material (600dp и 840dp). */
fun navLayoutFor(widthDp: Int): NavLayout = when {
    widthDp >= 840 -> NavLayout.RAIL_TWO_PANE
    widthDp >= 600 -> NavLayout.RAIL
    else -> NavLayout.BOTTOM_BAR
}
