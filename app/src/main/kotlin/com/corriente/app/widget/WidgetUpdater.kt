package com.corriente.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.corriente.app.AppContainer
import com.corriente.data.model.Account
import com.corriente.data.model.Category
import com.corriente.data.model.Txn
import com.corriente.money.Currency
import com.corriente.data.widget.WidgetConfigStore
import com.corriente.data.widget.WidgetSnapshotStore
import com.corriente.data.widget.buildWidgetSnapshot
import com.corriente.data.widget.defaultPinnedCurrencies
import com.corriente.money.CurrencyCode
import com.corriente.widget.CorrienteWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import com.corriente.data.widget.WidgetConfig
import com.corriente.data.widget.WidgetSnapshot
import java.time.LocalDate

/**
 * T4.2/T4.4: пересчитывает [com.corriente.data.widget.WidgetSnapshot] после любой записи в БД
 * (или смены настроек виджета) и пушит его в виджет.
 *
 * F2.1: не работает, пока виджет не размещён (проверка [GlanceAppWidgetManager]); балансы —
 * агрегат из SQL, а список операций — только недавнее окно (для месячных трат и частых категорий),
 * а не вся таблица `txn`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdater(
    private val appContext: Context,
    private val container: AppContainer,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = WidgetSnapshotStore(appContext)
    private val configStore = WidgetConfigStore(appContext)

    private suspend fun widgetPlaced(): Boolean =
        GlanceAppWidgetManager(appContext).getGlanceIds(CorrienteWidget::class.java).isNotEmpty()

    fun start() {
        // Пере-проверяем размещение при каждой смене настроек виджета и на старте (config
        // отдаёт начальное значение сразу). Пока виджета нет — тяжёлые подписки не открываются.
        configStore.config
            .map { widgetPlaced() }
            .distinctUntilChanged()
            .flatMapLatest { placed ->
                if (!placed) {
                    emptyFlow()
                } else {
                    val now = today()
                    val windowStart = now.minusDays(RECENT_WINDOW_DAYS)
                    val dbInputs = combine(
                        container.accountRepository.observeActive(),
                        container.txnRepository.observeRange(windowStart, now),
                        container.txnRepository.observeAccountDeltas(),
                        container.currencyRepository.observeAll(),
                        container.categoryRepository.observeActive(),
                    ) { accounts, recentTxns, deltas, currencies, categories ->
                        DbInputs(accounts, recentTxns, deltas, currencies, categories)
                    }
                    combine(dbInputs, configStore.config) { d, config -> assemble(d, config, now) }
                }
            }
            .conflate()
            .onEach { push(it) }
            .launchIn(scope)

        WidgetMidnightWorker.schedule(appContext) // F2.2: пересчёт на смене суток
    }

    /**
     * Разовый пересчёт снимка (F2.2): вызывается воркером на полночь, когда записи в БД не было,
     * а месячный итог/«сегодня» уже устарели. No-op, если виджет не размещён.
     */
    suspend fun refreshNow() {
        if (!widgetPlaced()) return
        val now = today()
        val windowStart = now.minusDays(RECENT_WINDOW_DAYS)
        val d = DbInputs(
            accounts = container.accountRepository.observeActive().first(),
            recentTxns = container.txnRepository.observeRange(windowStart, now).first(),
            deltas = container.txnRepository.observeAccountDeltas().first(),
            currencies = container.currencyRepository.observeAll().first(),
            categories = container.categoryRepository.observeActive().first(),
        )
        push(assemble(d, configStore.config.first(), now))
    }

    private suspend fun push(snapshot: WidgetSnapshot) {
        store.save(snapshot)
        CorrienteWidget().updateAll(appContext)
    }

    private fun assemble(d: DbInputs, config: WidgetConfig, now: LocalDate): WidgetSnapshot {
        val pinned = config.pinnedCurrencyCodes
            .mapNotNull { runCatching { CurrencyCode(it) }.getOrNull() }
            .ifEmpty { defaultPinnedCurrencies(d.accounts, d.recentTxns, now) }
        val activeAccountId = config.activeAccountId?.takeIf { id -> d.accounts.any { it.id == id } }
            ?: d.accounts.firstOrNull()?.id
            ?: ""
        return buildWidgetSnapshot(
            accounts = d.accounts,
            transactions = d.recentTxns,
            currencies = d.currencies,
            categories = d.categories,
            pinnedCurrencies = pinned,
            activeAccountId = activeAccountId,
            today = now,
            computedAt = System.currentTimeMillis(),
            accountDeltas = d.deltas,
        )
    }

    private data class DbInputs(
        val accounts: List<Account>,
        val recentTxns: List<Txn>,
        val deltas: Map<String, Long>,
        val currencies: List<Currency>,
        val categories: List<Category>,
    )

    private companion object {
        /** Покрывает окно месячных трат и 60-дневное окно частых категорий. */
        const val RECENT_WINDOW_DAYS = 62L
    }
}
