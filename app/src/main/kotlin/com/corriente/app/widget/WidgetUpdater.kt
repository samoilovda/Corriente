package com.corriente.app.widget

import android.content.Context
import com.corriente.app.AppContainer
import com.corriente.data.widget.WidgetSnapshotStore
import com.corriente.data.widget.buildWidgetSnapshot
import com.corriente.data.widget.defaultPinnedCurrencies
import androidx.glance.appwidget.updateAll
import com.corriente.widget.CorrienteWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate

/**
 * T4.2: пересчитывает [com.corriente.data.widget.WidgetSnapshot] после любой записи в БД и
 * пушит его в виджет. На периодическое обновление платформы полагаться нельзя
 * (ARCHITECTURE.md §4.4 п.1) — единственный надёжный путь это `updateAll` из процесса приложения.
 *
 * Закреплённые валюты и активный счёт пока берутся по умолчанию (T4.4 добавит их настройку).
 */
class WidgetUpdater(
    private val appContext: Context,
    private val container: AppContainer,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = WidgetSnapshotStore(appContext)

    fun start() {
        combine(
            container.accountRepository.observeActive(),
            container.txnRepository.observeAll(),
            container.currencyRepository.observeAll(),
            container.categoryRepository.observeActive(),
        ) { accounts, txns, currencies, categories ->
            val now = today()
            val pinned = defaultPinnedCurrencies(accounts, txns, now)
            val activeAccountId = accounts.firstOrNull()?.id ?: ""
            buildWidgetSnapshot(
                accounts = accounts,
                transactions = txns,
                currencies = currencies,
                categories = categories,
                pinnedCurrencies = pinned,
                activeAccountId = activeAccountId,
                today = now,
                computedAt = System.currentTimeMillis(),
            )
        }
            .conflate()
            .onEach { snapshot ->
                store.save(snapshot)
                CorrienteWidget().updateAll(appContext)
            }
            .launchIn(scope)
    }
}
