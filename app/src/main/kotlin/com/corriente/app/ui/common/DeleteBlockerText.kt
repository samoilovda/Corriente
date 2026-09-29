package com.corriente.app.ui.common

import com.corriente.app.R
import com.corriente.data.repository.DeleteBlocker

/** A2.2: сообщение с причиной отказа удалить счёт ([forAccount]) или категорию; берётся главная причина. */
fun Set<DeleteBlocker>.blockedMessage(forAccount: Boolean): UiMessage {
    val reason = DeleteBlocker.entries.first { it in this }
    return uiMessage(reason.messageRes(forAccount))
}

fun DeleteBlocker.messageRes(forAccount: Boolean): Int = when (this) {
    DeleteBlocker.TRANSACTIONS ->
        if (forAccount) R.string.delete_blocked_account_transactions else R.string.delete_blocked_category_transactions
    DeleteBlocker.RECURRENCES ->
        if (forAccount) R.string.delete_blocked_account_recurrences else R.string.delete_blocked_category_recurrences
    DeleteBlocker.BUDGETS -> R.string.delete_blocked_category_budgets
    DeleteBlocker.SUBCATEGORIES -> R.string.delete_blocked_category_subcategories
}
