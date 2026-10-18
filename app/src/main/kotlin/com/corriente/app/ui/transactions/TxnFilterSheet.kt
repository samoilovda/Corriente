package com.corriente.app.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.corriente.app.R
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * U01/U04: лист фильтров — черновик [TxnFilterDraft], применяемый целиком кнопкой «Применить».
 * Ошибка любого поля оставляет прежний фильтр и список без изменений.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TxnFilterSheet(
    state: TransactionsUiState,
    today: LocalDate,
    onApply: (TxnFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(TxnFilterDraft.of(state.filter, state.currencies)) }
    var errors by remember { mutableStateOf(FilterDraftErrors()) }
    var notApplied by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    fun edit(next: TxnFilterDraft, clear: FilterDraftErrors.() -> FilterDraftErrors) {
        draft = next
        errors = errors.clear()
        notApplied = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.txn_filters), style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDropdown(
                    label = state.accounts.firstOrNull { it.id == draft.accountId }?.name
                        ?: stringResource(R.string.txn_list_all_accounts),
                    allLabel = stringResource(R.string.txn_list_all_accounts),
                    options = state.accounts.map { it.id to it.name },
                    onSelect = { edit(draft.withAccount(it, state.accountCurrencies)) { copy(min = null, max = null) } },
                )
                FilterDropdown(
                    label = draft.currencyCode ?: stringResource(R.string.txn_list_all_currencies),
                    allLabel = stringResource(R.string.txn_list_all_currencies),
                    options = state.currencyCodes.map { it to it },
                    onSelect = { edit(draft.withCurrency(it, state.accountCurrencies)) { copy(min = null, max = null) } },
                )
            }
            FilterDropdown(
                label = state.categories.firstOrNull { it.id == draft.categoryId }?.name
                    ?: stringResource(R.string.txn_filter_all_categories),
                allLabel = stringResource(R.string.txn_filter_all_categories),
                options = state.categories.map { it.id to it.name },
                onSelect = { edit(draft.copy(categoryId = it)) { this } },
            )

            HorizontalDivider()
            Text(stringResource(R.string.txn_filter_period), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val thisMonth = draft.thisMonth(today)
                val lastMonth = draft.lastMonth(today)
                val allDates = draft.allDates()
                FilterChip(
                    selected = draft == thisMonth,
                    onClick = { edit(thisMonth) { copy(from = null, to = null) } },
                    label = { Text(stringResource(R.string.txn_filter_preset_this_month)) },
                )
                FilterChip(
                    selected = draft == lastMonth,
                    onClick = { edit(lastMonth) { copy(from = null, to = null) } },
                    label = { Text(stringResource(R.string.txn_filter_preset_last_month)) },
                )
                FilterChip(
                    selected = draft == allDates,
                    onClick = { edit(allDates) { copy(from = null, to = null) } },
                    label = { Text(stringResource(R.string.txn_filter_preset_all_dates)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterTextField(
                    value = draft.fromText,
                    onValueChange = { edit(draft.copy(fromText = it)) { copy(from = null) } },
                    label = stringResource(R.string.txn_filter_from),
                    placeholder = stringResource(R.string.txn_filter_date_placeholder),
                    error = errors.from?.let { filterErrorText(it, null) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                FilterTextField(
                    value = draft.toText,
                    onValueChange = { edit(draft.copy(toText = it)) { copy(to = null, from = from.takeUnless { it == FilterFieldError.PERIOD_REVERSED }) } },
                    label = stringResource(R.string.txn_filter_to),
                    placeholder = stringResource(R.string.txn_filter_date_placeholder),
                    error = errors.to?.let { filterErrorText(it, null) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.txn_filter_pick_dates), Modifier.padding(start = 8.dp))
            }

            HorizontalDivider()
            val amountCode = draft.amountCurrencyCode(state.accountCurrencies)
            val amountCurrency = amountCode?.let(state.currencies::get)
            if (amountCurrency == null) {
                Text(
                    stringResource(R.string.txn_filter_amount_no_currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.txn_filter_amount_in, amountCurrency.code.code),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (draft.amountResetByCurrencyChange) {
                Text(
                    stringResource(R.string.txn_filter_amount_reset),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterTextField(
                    value = draft.minText,
                    onValueChange = { edit(draft.copy(minText = it)) { copy(min = null) } },
                    label = stringResource(R.string.txn_filter_amount_min),
                    error = errors.min?.let { filterErrorText(it, amountCurrency) },
                    keyboardType = KeyboardType.Decimal,
                    enabled = amountCurrency != null,
                    modifier = Modifier.weight(1f),
                )
                FilterTextField(
                    value = draft.maxText,
                    onValueChange = { edit(draft.copy(maxText = it)) { copy(max = null, min = min.takeUnless { it == FilterFieldError.AMOUNT_MIN_GT_MAX }) } },
                    label = stringResource(R.string.txn_filter_amount_max),
                    error = errors.max?.let { filterErrorText(it, amountCurrency) },
                    keyboardType = KeyboardType.Decimal,
                    enabled = amountCurrency != null,
                    modifier = Modifier.weight(1f),
                )
            }

            if (notApplied) {
                Text(
                    stringResource(R.string.txn_filter_not_applied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.txn_filter_clear)) }
                Button(onClick = {
                    when (val check = draft.validate(state.filter, state.accountCurrencies, state.currencies)) {
                        is FilterDraftCheck.Valid -> {
                            onApply(check.filter)
                            onDismiss()
                        }
                        is FilterDraftCheck.Invalid -> {
                            errors = check.errors
                            notApplied = true
                        }
                    }
                }) { Text(stringResource(R.string.txn_filter_apply)) }
            }
        }
    }

    if (pickerOpen) {
        val initialFrom = runCatching { LocalDate.parse(draft.fromText.trim()) }.getOrNull()
        val initialTo = runCatching { LocalDate.parse(draft.toText.trim()) }.getOrNull()
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialFrom?.toUtcMillis(),
            initialSelectedEndDateMillis = initialTo?.takeIf { initialFrom == null || it >= initialFrom }?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val from = pickerState.selectedStartDateMillis?.toUtcDate()
                        // Выбран один день — период из одного дня.
                        val to = pickerState.selectedEndDateMillis?.toUtcDate() ?: from
                        edit(draft.withPeriod(from, to)) { copy(from = null, to = null) }
                        pickerOpen = false
                    },
                ) { Text(stringResource(R.string.txn_filter_dates_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

/** DatePicker отдаёт полночь UTC выбранного дня — без часового пояса устройства. */
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

@Composable
private fun filterErrorText(error: FilterFieldError, currency: Currency?): String = when (error) {
    FilterFieldError.DATE_INVALID -> stringResource(R.string.txn_filter_error_date)
    FilterFieldError.PERIOD_REVERSED -> stringResource(R.string.txn_filter_error_period_reversed)
    FilterFieldError.AMOUNT_NOT_A_NUMBER -> stringResource(R.string.txn_filter_error_amount_nan)
    FilterFieldError.AMOUNT_NEGATIVE -> stringResource(R.string.txn_filter_error_amount_negative)
    FilterFieldError.AMOUNT_TOO_MANY_DECIMALS -> when {
        currency == null -> stringResource(R.string.txn_filter_error_amount_nan)
        currency.minorUnits == 0 -> stringResource(R.string.txn_filter_error_amount_no_decimals, currency.code.code)
        else -> stringResource(R.string.txn_filter_error_amount_decimals, currency.code.code, currency.minorUnits)
    }
    FilterFieldError.AMOUNT_OVERFLOW -> stringResource(R.string.txn_filter_error_amount_overflow)
    FilterFieldError.AMOUNT_MIN_GT_MAX -> stringResource(R.string.txn_filter_error_min_gt_max)
}

@Composable
internal fun FilterDropdown(
    label: String,
    allLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

/**
 * U04/U09: действующие параметры фильтра над списком; крестик снимает ровно один параметр.
 * Запрос поиска виден в своём поле и здесь не дублируется.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveFilterChips(
    state: TransactionsUiState,
    onRemoveAccount: () -> Unit,
    onRemoveCurrency: () -> Unit,
    onRemoveCategory: () -> Unit,
    onRemovePeriod: () -> Unit,
    onRemoveAmount: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = state.filter
    val chips = buildList {
        filter.accountId?.let { id ->
            add((state.accounts.firstOrNull { it.id == id }?.name ?: id) to onRemoveAccount)
        }
        filter.currencyCode?.let { add(it to onRemoveCurrency) }
        filter.categoryId?.let { id ->
            add((state.categories.firstOrNull { it.id == id }?.name ?: id) to onRemoveCategory)
        }
        periodLabel(filter.from, filter.to)?.let { add(it to onRemovePeriod) }
        filter.amount?.let { range -> amountLabel(range, state.currencies)?.let { add(it to onRemoveAmount) } }
    }
    if (chips.isEmpty()) return
    val activeLabel = stringResource(R.string.txn_filter_active)
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)
            .semantics { contentDescription = activeLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { (label, onRemove) ->
            val removeLabel = stringResource(R.string.txn_filter_chip_remove, label)
            InputChip(
                selected = true,
                onClick = onRemove,
                label = { Text(label) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
                modifier = Modifier.semantics { contentDescription = removeLabel },
            )
        }
        TextButton(onClick = onClearAll) { Text(stringResource(R.string.txn_filter_clear)) }
    }
}

@Composable
private fun periodLabel(from: LocalDate?, to: LocalDate?): String? {
    val format = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return when {
        from != null && to != null -> stringResource(R.string.txn_filter_chip_range, format.format(from), format.format(to))
        from != null -> stringResource(R.string.txn_filter_chip_from, format.format(from))
        to != null -> stringResource(R.string.txn_filter_chip_to, format.format(to))
        else -> null
    }
}

@Composable
private fun amountLabel(range: AmountRange, currencies: Map<String, Currency>): String? {
    val currency = currencies[range.currencyCode] ?: return null
    fun text(minor: Long) = MoneyFormatter.format(Money(Minor(minor), CurrencyCode(range.currencyCode)), currency)
    return when {
        range.minMinor != null && range.maxMinor != null ->
            stringResource(R.string.txn_filter_chip_range, text(range.minMinor), text(range.maxMinor))
        range.minMinor != null -> stringResource(R.string.txn_filter_chip_amount_min, text(range.minMinor))
        range.maxMinor != null -> stringResource(R.string.txn_filter_chip_amount_max, text(range.maxMinor))
        else -> null
    }
}
