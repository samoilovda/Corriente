package com.corriente.data.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Какой счёт открывать на «Операциях» при запуске приложения. */
enum class StartAccountMode {
    /** Счёт последней внесённой операции. */
    LAST_TRANSACTION,

    /** Ничего не выбирать: шапка по валюте первого счёта в списке, список — по всем счетам (как было раньше). */
    FIRST,

    /** Всегда один выбранный счёт. */
    PINNED,
}

data class StartAccountSetting(
    val mode: StartAccountMode = StartAccountMode.LAST_TRANSACTION,
    /** Счёт для [StartAccountMode.PINNED]. */
    val pinnedAccountId: String? = null,
)

/** Настройка живёт на устройстве (как остальные UI-настройки): в бэкап и синхронизацию не входит. */
interface StartAccountSettings {
    val state: StateFlow<StartAccountSetting>

    fun set(setting: StartAccountSetting)
}

open class InMemoryStartAccountSettings(initial: StartAccountSetting = StartAccountSetting()) : StartAccountSettings {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<StartAccountSetting> = flow

    override fun set(setting: StartAccountSetting) = flow.update { setting }
}

/**
 * Счёт для открытия при запуске или null — ничего не выбирать (поведение «первый в списке»).
 * [activeAccountIds] — активные счета в порядке списка, [recentAccountIds] — счета последних операций, новые первыми.
 * Закреплённый счёт, который удалили или архивировали, уступает место счёту последней операции; архивные счета
 * последних операций пропускаются.
 */
fun resolveStartAccountId(
    setting: StartAccountSetting,
    activeAccountIds: List<String>,
    recentAccountIds: List<String>,
): String? {
    if (setting.mode == StartAccountMode.FIRST) return null
    if (setting.mode == StartAccountMode.PINNED && setting.pinnedAccountId in activeAccountIds) return setting.pinnedAccountId
    return recentAccountIds.firstOrNull { it in activeAccountIds }
}
