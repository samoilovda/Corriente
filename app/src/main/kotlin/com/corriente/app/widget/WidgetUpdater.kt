package com.corriente.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.corriente.app.AppContainer
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.data.widget.WidgetSnapshotStore
import com.corriente.data.widget.buildWidgetSnapshot
import com.corriente.data.widget.defaultPinnedCurrencies
import com.corriente.money.CurrencyCode
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
 * T4.2/T4.4: пересчитывает [com.corriente.data.widget.WidgetSnapshot] после любой записи в БД
 * (или смены настроек виджета) и пушит его в виджет. На периодическое обновление платформы
 * полагаться нельзя (ARCHITECTURE.md §4.4 п.1) — единственный надёжный путь `updateAll`.
 */
class WidgetUpdater(
    private val appContext: Context,
    private val container: AppContainer,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = WidgetSnapshotStore(appContext)
    private val configStore = WidgetConfigStore(appContext)

    fun start() {
        combine(
            container.accountRepository.observeActive(),
            container.txnRepository.observeAll(),
            container.currencyRepository.observeAll(),
            container.categoryRepository.observeActive(),
            configStore.config,
        ) { accounts, txns, currencies, categories, config ->
            val now = today()
            val pinned = config.pinnedCurrencyCodes
                .mapNotNull { runCatching { CurrencyCode(it) }.getOrNull() }
                .ifEmpty { defaultPinnedCurrencies(accounts, txns, now) }
            val activeAccountId = config.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id
                ?: ""
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
