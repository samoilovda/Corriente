package com.corriente.app

import com.corriente.data.db.AppDatabase

/**
 * R5.1: общая зачистка перед UI-тестами, гоняемыми через настоящую [MainActivity] (а значит —
 * через настоящую БД устройства/эмулятора, ADR-011 — без Hilt-тестового контейнера подменить
 * репозитории нечем). Справочник валют не трогаем — он засеян один раз при первом создании БД
 * ([AppDatabase.seedCallback]) и не пересоздаётся между прогонами.
 */
suspend fun AppDatabase.clearMutableTables() {
    txnDao().deleteAll()
    categoryDao().deleteAll()
    accountDao().deleteAll()
}
