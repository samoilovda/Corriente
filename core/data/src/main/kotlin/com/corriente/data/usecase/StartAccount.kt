package com.corriente.data.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Какой счёт или валюту открывать на «Операциях» при запуске приложения. */
enum class StartAccountMode {
    /** Счёт последней внесённой операции. */
    LAST_TRANSACTION,

    /** Как приложение было оставлено: тот же счёт или «Все счета» в той же валюте, что пользователь выбрал в шапке. */
    LAST_SELECTED,

    /** «Все счета» в валюте счёта последней операции: список не сужается, шапка показывает нужную валюту. */
    LAST_CURRENCY,

    /** Самый частый счёт за последние [MOST_USED_DAYS] дней. */
    MOST_USED,

    /** Ничего не выбирать: шапка по валюте первого счёта в списке, список — по всем счетам (как было раньше). */
    FIRST,

    /** Всегда один выбранный счёт. */
    PINNED,
}

/** За сколько последних дней считается «самый частый счёт». */
const val MOST_USED_DAYS = 30L

/** Что выбрано в шапке: [accountId] — конкретный счёт; иначе «Все счета» в валюте [currency] (null — не выбрана). */
data class StartSelection(val accountId: String? = null, val currency: String? = null)

data class StartAccountSetting(
    val mode: StartAccountMode = StartAccountMode.LAST_TRANSACTION,
    /** Счёт для [StartAccountMode.PINNED]. */
    val pinnedAccountId: String? = null,
    /** Последний выбор пользователя в шапке для [StartAccountMode.LAST_SELECTED]; null — ещё не выбирал. */
    val lastSelection: StartSelection? = null,
)

/** Настройка живёт на устройстве (как остальные UI-настройки): в бэкап и синхронизацию не входит. */
interface StartAccountSettings {
    val state: StateFlow<StartAccountSetting>

    fun update(transform: (StartAccountSetting) -> StartAccountSetting)
}

/** Запомнить выбор пользователя в шапке (для [StartAccountMode.LAST_SELECTED]); режим и закреплённый счёт не трогает. */
fun StartAccountSettings.rememberSelection(selection: StartSelection) = update { it.copy(lastSelection = selection) }

open class InMemoryStartAccountSettings(initial: StartAccountSetting = StartAccountSetting()) : StartAccountSettings {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<StartAccountSetting> = flow

    override fun update(transform: (StartAccountSetting) -> StartAccountSetting) = flow.update(transform)
}

/** Активный счёт для выбора стартового: id и код его валюты. */
data class StartAccountInfo(val id: String, val currency: String)

/**
 * Что открыть при запуске или null — ничего не выбирать (поведение «первый в списке»).
 * [active] — активные счета в порядке списка, [recentAccountIds] — счета последних операций (новые первыми),
 * [frequentAccountIds] — счета по убыванию числа операций за [MOST_USED_DAYS] дней. Режимы без данных
 * (нет операций, нет выбора, закреплённый счёт удалён или архивирован) откатываются к счёту последней операции;
 * архивные счета везде пропускаются.
 */
fun resolveStartSelection(
    setting: StartAccountSetting,
    active: List<StartAccountInfo>,
    recentAccountIds: List<String>,
    frequentAccountIds: List<String> = emptyList(),
): StartSelection? {
    val byId = active.associateBy { it.id }
    val lastAccount = recentAccountIds.firstNotNullOfOrNull { byId[it] }
    val lastTransaction = lastAccount?.let { StartSelection(accountId = it.id) }
    return when (setting.mode) {
        StartAccountMode.FIRST -> null
        StartAccountMode.LAST_TRANSACTION -> lastTransaction
        StartAccountMode.LAST_CURRENCY -> lastAccount?.let { StartSelection(currency = it.currency) }
        StartAccountMode.PINNED ->
            setting.pinnedAccountId?.takeIf { it in byId }?.let { StartSelection(accountId = it) } ?: lastTransaction
        StartAccountMode.MOST_USED ->
            frequentAccountIds.firstNotNullOfOrNull { byId[it] }?.let { StartSelection(accountId = it.id) } ?: lastTransaction
        StartAccountMode.LAST_SELECTED -> {
            val chosen = setting.lastSelection ?: return lastTransaction
            when {
                chosen.accountId != null ->
                    if (chosen.accountId in byId) StartSelection(accountId = chosen.accountId) else lastTransaction
                chosen.currency != null && active.any { it.currency == chosen.currency } -> StartSelection(currency = chosen.currency)
                else -> null // выбрали «Все счета» без валюты, либо валюты больше нет
            }
        }
    }
}
