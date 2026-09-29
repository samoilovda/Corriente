package com.corriente.data.repository

/** A1.7: чем занят счёт/категория и потому не может быть физически удалён (нужна архивация). */
enum class DeleteBlocker { TRANSACTIONS, RECURRENCES, BUDGETS, SUBCATEGORIES }

/** A1.7: итог `deleteIfUnused` — экран показывает причину отказа, а не молчит. */
sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotFound : DeleteResult
    data class Blocked(val reasons: Set<DeleteBlocker>) : DeleteResult
}
