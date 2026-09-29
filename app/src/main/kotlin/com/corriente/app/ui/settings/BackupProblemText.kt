package com.corriente.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.corriente.app.R
import com.corriente.data.backup.BackupEntity
import com.corriente.data.backup.BackupField
import com.corriente.data.backup.BackupProblem
import com.corriente.data.backup.TransferIssue

/** A2.13: текст проблемы бэкапа — [BackupProblem] несёт только структуру, локаль подставляет экран. */
@Composable
fun backupProblemText(problem: BackupProblem): String {
    val who = stringResource(entityName(problem.entity), problem.ref)
    return when (problem) {
        is BackupProblem.DanglingReference ->
            stringResource(R.string.backup_problem_dangling, who, stringResource(entityName(problem.target), problem.targetId))
        is BackupProblem.InvalidValue ->
            stringResource(R.string.backup_problem_invalid_value, who, stringResource(fieldName(problem.field)), problem.value)
        is BackupProblem.NonPositiveAmount -> stringResource(R.string.backup_problem_non_positive, who, problem.amountMinor)
        is BackupProblem.DuplicateKey -> stringResource(R.string.backup_problem_duplicate, who)
        is BackupProblem.CurrencyMismatch ->
            stringResource(R.string.backup_problem_currency_mismatch, who, problem.actual, problem.expected)
        is BackupProblem.BadTransfer -> stringResource(
            when (problem.issue) {
                TransferIssue.MISSING_SECOND_SIDE -> R.string.backup_problem_transfer_second_side
                TransferIssue.HAS_CATEGORY -> R.string.backup_problem_transfer_category
                TransferIssue.SAME_ACCOUNT -> R.string.backup_problem_transfer_same_account
            },
            who,
        )
        is BackupProblem.UnexpectedSecondSide -> stringResource(R.string.backup_problem_unexpected_second_side, who)
        is BackupProblem.NestedCategory -> stringResource(R.string.backup_problem_nested_category, who)
        is BackupProblem.KindMismatch -> stringResource(R.string.backup_problem_kind_mismatch, who)
        is BackupProblem.Unreadable -> stringResource(R.string.backup_problem_unreadable, who, problem.detail)
    }
}

private fun entityName(entity: BackupEntity): Int = when (entity) {
    BackupEntity.CURRENCY -> R.string.backup_entity_currency
    BackupEntity.ACCOUNT -> R.string.backup_entity_account
    BackupEntity.CATEGORY -> R.string.backup_entity_category
    BackupEntity.TRANSACTION -> R.string.backup_entity_transaction
    BackupEntity.IMPORT_BATCH -> R.string.backup_entity_import_batch
    BackupEntity.IMPORT_ALIAS -> R.string.backup_entity_import_alias
    BackupEntity.BUDGET -> R.string.backup_entity_budget
    BackupEntity.RECURRENCE -> R.string.backup_entity_recurrence
}

private fun fieldName(field: BackupField): Int = when (field) {
    BackupField.CURRENCY_CODE -> R.string.backup_field_currency_code
    BackupField.TO_CURRENCY_CODE -> R.string.backup_field_to_currency_code
    BackupField.MINOR_UNITS -> R.string.backup_field_minor_units
    BackupField.DISPLAY_SCALE -> R.string.backup_field_display_scale
    BackupField.KIND -> R.string.backup_field_kind
    BackupField.ACCOUNT_KIND -> R.string.backup_field_kind
    BackupField.ORIGIN -> R.string.backup_field_origin
    BackupField.DATE -> R.string.backup_field_date
    BackupField.PERIOD -> R.string.backup_field_period
    BackupField.RULE_TYPE -> R.string.backup_field_rule_type
    BackupField.DAY_OF_MONTH -> R.string.backup_field_day_of_month
    BackupField.INTERVAL -> R.string.backup_field_interval
    BackupField.ALIAS_KIND -> R.string.backup_field_alias_kind
}

/** Первые три проблемы списком + «и ещё N». */
@Composable
fun backupProblemsSummary(problems: List<BackupProblem>): String {
    val shown = problems.take(3).map { "• " + backupProblemText(it) }.joinToString("\n")
    val rest = problems.size - 3
    return shown + if (rest > 0) "\n" + stringResource(R.string.backup_result_invalid_more, rest) else ""
}
