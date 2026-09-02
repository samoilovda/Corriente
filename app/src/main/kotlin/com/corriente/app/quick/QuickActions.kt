package com.corriente.app.quick

import com.corriente.data.repository.TxnRepository
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.money.Money
import java.time.LocalDate

/**
 * Запись из окон быстрого ввода виджета (F0.6). Вынесено из Activity, чтобы:
 *  - запускать в `lifecycleScope`, а не в скоупе композиции (переживает пересоздание Activity);
 *  - ошибку репозитория возвращать как [Result], а не ронять процесс;
 *  - покрыть юнит-тестом («ошибка записи не приводит к finish»).
 */
suspend fun addQuickExpense(
    txns: TxnRepository,
    accountId: String,
    money: Money,
    categoryId: String?,
    today: LocalDate = LocalDate.now(),
): Result<Unit> = runCatching {
    txns.addExpense(accountId, money, categoryId, today, null)
}

suspend fun changeActiveAccount(configStore: WidgetConfigStore, accountId: String): Result<Unit> = runCatching {
    configStore.setActiveAccount(accountId)
}
