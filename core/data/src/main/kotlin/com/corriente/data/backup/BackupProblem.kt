package com.corriente.data.backup

/** Таблица бэкапа, к которой относится проблема ([BackupProblem]). Названия для экрана — в ресурсах `:app`. */
enum class BackupEntity { CURRENCY, ACCOUNT, CATEGORY, TRANSACTION, IMPORT_BATCH, IMPORT_ALIAS, BUDGET, RECURRENCE }

/** Поле записи, в котором найдено недопустимое значение ([BackupProblem.InvalidValue]). */
enum class BackupField {
    CURRENCY_CODE, TO_CURRENCY_CODE, MINOR_UNITS, DISPLAY_SCALE, KIND, ORIGIN, DATE, PERIOD, RULE_TYPE,
    DAY_OF_MONTH, INTERVAL, ALIAS_KIND, ACCOUNT_KIND,
}

/** Что не так с переводом ([BackupProblem.BadTransfer]). */
enum class TransferIssue { MISSING_SECOND_SIDE, HAS_CATEGORY, SAME_ACCOUNT }

/**
 * A1.12/A2.13: проблема, найденная [BackupRepository.validate] до записи. Только структурные данные, без
 * готового текста: текст под локаль строит экран через `stringResource` (ROADMAP.md §8). [ref] — id записи
 * (или ключ, если у неё нет id).
 *
 * Правило `validate`: всё, что может бросить `toEntity()`, вставка в SQLite или последующий `toDomain()`,
 * ловится здесь — иначе повреждённый файл проходит «Проверить файл», а после восстановления приложение не
 * запускается.
 */
sealed interface BackupProblem {
    val entity: BackupEntity
    val ref: String

    /** Ссылка [entity]/[ref] на несуществующую запись [target]/[targetId]. */
    data class DanglingReference(
        override val entity: BackupEntity, override val ref: String,
        val target: BackupEntity, val targetId: String,
    ) : BackupProblem

    /** Недопустимое значение поля (формат кода валюты, неизвестное имя enum, дата, диапазон). */
    data class InvalidValue(
        override val entity: BackupEntity, override val ref: String,
        val field: BackupField, val value: String,
    ) : BackupProblem

    /** Сумма должна быть положительной (I-1). */
    data class NonPositiveAmount(
        override val entity: BackupEntity, override val ref: String, val amountMinor: Long,
    ) : BackupProblem

    /** Повторяющийся id/ключ там, где нужна уникальность (id, `(name, kind)` категорий, `import_hash`). */
    data class DuplicateKey(override val entity: BackupEntity, override val ref: String) : BackupProblem

    /** I-15: валюта записи не равна валюте её счёта. */
    data class CurrencyMismatch(
        override val entity: BackupEntity, override val ref: String,
        val actual: String, val expected: String,
    ) : BackupProblem

    /** Перевод собран неверно (I-11). */
    data class BadTransfer(override val ref: String, val issue: TransferIssue) : BackupProblem {
        override val entity: BackupEntity get() = BackupEntity.TRANSACTION
    }

    /** У расхода/дохода есть вторая сторона перевода (I-11). */
    data class UnexpectedSecondSide(override val ref: String) : BackupProblem {
        override val entity: BackupEntity get() = BackupEntity.TRANSACTION
    }

    /** Допустима одна степень вложенности категорий, родитель не может быть сам себе родителем. */
    data class NestedCategory(override val ref: String) : BackupProblem {
        override val entity: BackupEntity get() = BackupEntity.CATEGORY
    }

    /** Запись не читается доменной моделью, хотя явные проверки этого не заметили (страховка). */
    data class Unreadable(
        override val entity: BackupEntity, override val ref: String, val detail: String,
    ) : BackupProblem
}
