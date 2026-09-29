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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
 *
 * R4.0: размещение перепроверяется не только по эмиссиям [WidgetConfigStore.config], но и по
 * сигналу [notifyPlacementChanged] — иначе виджет, добавленный при уже запущенном процессе без
 * захода в настройки, оставался бы на `EMPTY`/старом снимке до следующего запуска приложения.
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

    /**
     * R4.0: сигнал «пере-проверь размещение виджета прямо сейчас» помимо обычных триггеров
     * (смена настроек, старт процесса). [WidgetRefreshReceiver] дёргает это, когда виджет
     * добавлен на рабочий стол при уже запущенном процессе — тогда `configStore.config` не
     * эмиттит ничего нового, и без этого сигнала [widgetPlaced] остался бы false до следующего
     * запуска приложения или ручной смены настроек.
     */
    private val recheckPlacement = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A1.2: «перечитай дату сейчас» — из [refreshNow]/полночного воркера, кроме периодической проверки. */
    private val dateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private suspend fun widgetPlaced(): Boolean =
        GlanceAppWidgetManager(appContext).getGlanceIds(CorrienteWidget::class.java).isNotEmpty()

    fun start() {
        // Пере-проверяем размещение при каждой смене настроек виджета, на старте (config отдаёт
        // начальное значение сразу) и по внешнему сигналу (R4.0). Пока виджета нет — тяжёлые
        // подписки не открываются.
        merge(configStore.config.map { }, recheckPlacement)
            .map { widgetPlaced() }
            .distinctUntilChanged()
            .flatMapLatest { placed ->
                if (!placed) {
                    emptyFlow()
                } else {
                    // A1.2: дата — часть реактивного ключа. Подписка на окно операций и «сегодня» в
                    // assemble пересоздаются при смене суток, а не живут с датой момента подписки.
                    widgetSnapshots(
                        dateTicker = dateTicker(today, dateSignal),
                        accounts = container.accountRepository.observeActive(),
                        txnsInRange = container.txnRepository::observeRange,
                        deltas = container.txnRepository.observeAccountDeltas(),
                        currencies = container.currencyRepository.observeAll(),
                        categories = container.categoryRepository.observeActive(),
                        config = configStore.config,
                        clock = System::currentTimeMillis,
                    )
                }
            }
            .conflate()
            .onEach { push(it) }
            .launchIn(scope)

        WidgetMidnightWorker.schedule(appContext) // F2.2: пересчёт на смене суток
    }

    /** R4.0: вызывается [WidgetRefreshReceiver] при ACTION_APPWIDGET_ENABLED/UPDATE. */
    fun notifyPlacementChanged() {
        recheckPlacement.tryEmit(Unit)
    }

    /**
     * Разовый пересчёт снимка (F2.2): вызывается воркером на полночь, когда записи в БД не было,
     * а месячный итог/«сегодня» уже устарели. No-op, если виджет не размещён.
     */
    suspend fun refreshNow() {
        dateSignal.tryEmit(Unit)
        if (!widgetPlaced()) return
        val now = today()
        val windowStart = now.minusDays(WIDGET_RECENT_WINDOW_DAYS)
        val d = DbInputs(
            accounts = container.accountRepository.observeActive().first(),
            recentTxns = container.txnRepository.observeRange(windowStart, now).first(),
            deltas = container.txnRepository.observeAccountDeltas().first(),
            currencies = container.currencyRepository.observeAll().first(),
            categories = container.categoryRepository.observeActive().first(),
        )
        push(assembleSnapshot(d, configStore.config.first(), now, System.currentTimeMillis()))
    }

    private suspend fun push(snapshot: WidgetSnapshot) {
        store.save(snapshot)
        CorrienteWidget().updateAll(appContext)
    }
}

/** Покрывает окно месячных трат и 60-дневное окно частых категорий. */
internal const val WIDGET_RECENT_WINDOW_DAYS = 62L

internal data class DbInputs(
    val accounts: List<Account>,
    val recentTxns: List<Txn>,
    val deltas: Map<String, Long>,
    val currencies: List<Currency>,
    val categories: List<Category>,
)

/**
 * A1.2: эмитит `today()` сразу, затем раз в [DATE_POLL_MS] и по [signal]; повторы отбрасываются, так что
 * подписчик видит только реальную смену даты (полночь, ручной перевод часов, смена часового пояса).
 */
internal fun dateTicker(
    today: () -> LocalDate,
    signal: Flow<Unit> = emptyFlow(),
    pollMs: Long = DATE_POLL_MS,
): Flow<LocalDate> = merge(
    flow { while (true) { emit(Unit); delay(pollMs) } },
    signal,
).map { today() }.distinctUntilChanged()

private const val DATE_POLL_MS = 30_000L

/** A1.2: поток снимков виджета; вся зависимость от «сегодня» — через [dateTicker] (тестируется без Android). */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun widgetSnapshots(
    dateTicker: Flow<LocalDate>,
    accounts: Flow<List<Account>>,
    txnsInRange: (LocalDate, LocalDate) -> Flow<List<Txn>>,
    deltas: Flow<Map<String, Long>>,
    currencies: Flow<List<Currency>>,
    categories: Flow<List<Category>>,
    config: Flow<WidgetConfig>,
    clock: () -> Long,
): Flow<WidgetSnapshot> = dateTicker.flatMapLatest { now ->
    val windowStart = now.minusDays(WIDGET_RECENT_WINDOW_DAYS)
    val dbInputs = combine(
        accounts, txnsInRange(windowStart, now), deltas, currencies, categories,
    ) { a, recent, d, cur, cat -> DbInputs(a, recent, d, cur, cat) }
    combine(dbInputs, config) { d, c -> assembleSnapshot(d, c, now, clock()) }
}

internal fun assembleSnapshot(d: DbInputs, config: WidgetConfig, now: LocalDate, computedAt: Long): WidgetSnapshot {
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
        computedAt = computedAt,
        accountDeltas = d.deltas,
        pinnedCategoryIds = config.pinnedCategoryIds,
    )
}
