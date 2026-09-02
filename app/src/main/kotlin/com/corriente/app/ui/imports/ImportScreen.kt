package com.corriente.app.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.data.imports.MonefyRowIssue
import com.corriente.data.imports.ReviewDecision
import com.corriente.data.imports.ReviewReason
import com.corriente.data.imports.ReviewRef
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor

/**
 * T3.3: обязательный dry-run импорта Monefy. Показывает, что будет создано, и даёт разрешить
 * позиции NEEDS_REVIEW прямо здесь (склейка пар, аномальный курс, округление, валюта счёта).
 * Запись — только по кнопке «Импортировать» (I-19: повторный импорт того же файла идемпотентен).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.factory(corrienteContainer().monefyImportRepository),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "monefy.csv"
            context.contentResolver.openInputStream(uri)?.let { viewModel.preview(it, name) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                ImportUiState.Idle -> {
                    Button(onClick = {
                        picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                    }) { Text(stringResource(R.string.import_pick_file)) }
                }

                ImportUiState.Working -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.import_working))
                }

                is ImportUiState.Ready -> ReadyBody(s, viewModel::chooseDecision, viewModel::confirm)

                is ImportUiState.Done -> {
                    Text(
                        stringResource(R.string.import_done, s.inserted, s.skipped),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.import_restart)) }
                }

                is ImportUiState.Failed -> {
                    Text(
                        stringResource(R.string.import_failed, importFailureText(s.reason)),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.import_restart)) }
                }
            }
        }
    }
}

@Composable
private fun ReadyBody(
    ready: ImportUiState.Ready,
    onDecision: (ReviewRef, ReviewDecision?) -> Unit,
    onConfirm: () -> Unit,
) {
    val summary = ready.summary
    Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.import_summary_accounts, summary.accounts, summary.openingBalances))
    Text(stringResource(R.string.import_summary_categories, summary.categories))
    Text(stringResource(R.string.import_summary_operations, summary.operations))
    Text(stringResource(R.string.import_summary_transfers, summary.transfers))
    if (summary.unpairedHalves > 0) {
        Text(stringResource(R.string.import_summary_unpaired, summary.unpairedHalves))
    }

    if (ready.reviews.isNotEmpty()) {
        HorizontalDivider()
        val unresolved = ready.reviews.count { it.decision == null }
        Text(
            stringResource(R.string.import_reviews_title, unresolved, ready.reviews.size),
            style = MaterialTheme.typography.titleSmall,
        )
        ready.reviews.forEach { ReviewCardView(it, onDecision) }
    }

    if (summary.errors.isNotEmpty()) {
        HorizontalDivider()
        Text(
            stringResource(R.string.import_errors_title, summary.errors.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        summary.errors.forEach {
            Text("• ${stringResource(R.string.import_error_row_prefix, it.line, monefyRowIssueText(it.issue))}", color = MaterialTheme.colorScheme.error)
        }
    }

    HorizontalDivider()
    Text(stringResource(R.string.import_confirm_hint), style = MaterialTheme.typography.bodySmall)
    val blocked = ready.reviews.any {
        it.reason == ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH && it.decision == null
    }
    Button(onClick = onConfirm, enabled = !blocked) { Text(stringResource(R.string.import_confirm)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCardView(card: ReviewCard, onDecision: (ReviewRef, ReviewDecision?) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("• ${reviewCardMessage(card)}", style = MaterialTheme.typography.bodyMedium)

        if (card.decision == null) {
            val hint = if (card.reason == ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH) {
                R.string.import_review_blocked
            } else {
                R.string.import_review_hint_unresolved
            }
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (card.reason) {
                ReviewReason.AMBIGUOUS_PAIRING -> {
                    DecisionChip(R.string.import_review_accept, card.decision is ReviewDecision.Accept) {
                        onDecision(card.ref, ReviewDecision.Accept)
                    }
                    DecisionChip(R.string.import_review_keep_separate, card.decision is ReviewDecision.KeepSeparate) {
                        onDecision(card.ref, ReviewDecision.KeepSeparate)
                    }
                }

                ReviewReason.ANOMALOUS_CURRENCY -> {
                    DecisionChip(R.string.import_review_accept, card.decision is ReviewDecision.Accept) {
                        onDecision(card.ref, ReviewDecision.Accept)
                    }
                    DecisionChip(R.string.import_review_same_currency, card.decision is ReviewDecision.SameCurrency) {
                        onDecision(card.ref, ReviewDecision.SameCurrency)
                    }
                }

                ReviewReason.EXCESS_PRECISION -> {
                    DecisionChip(R.string.import_review_accept, card.decision is ReviewDecision.Accept) {
                        onDecision(card.ref, ReviewDecision.Accept)
                    }
                }

                ReviewReason.ACCOUNT_CURRENCY_CONFLICT -> {
                    card.currencyChoices.forEach { code ->
                        val chosen = (card.decision as? ReviewDecision.AccountCurrency)?.code?.code == code
                        DecisionChip(code, chosen) {
                            onDecision(card.ref, ReviewDecision.AccountCurrency(CurrencyCode(code)))
                        }
                    }
                }

                ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH -> {
                    val label = stringResource(
                        R.string.import_review_separate_account,
                        card.currencyChoices.firstOrNull().orEmpty(),
                    )
                    DecisionChip(label, card.decision is ReviewDecision.SeparateAccount) {
                        onDecision(card.ref, ReviewDecision.SeparateAccount)
                    }
                }
            }
        }

        if (card.decision != null) {
            TextButton(onClick = { onDecision(card.ref, null) }) {
                Text(stringResource(R.string.import_review_reset))
            }
        }

        if (card.reason == ReviewReason.EXCESS_PRECISION &&
            card.fromAmountMinor != null && card.toAmountMinor != null
        ) {
            ExactAmountsEditor(card, onDecision)
        }
    }
}

@Composable
private fun DecisionChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(labelRes)) })
}

@Composable
private fun DecisionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ExactAmountsEditor(card: ReviewCard, onDecision: (ReviewRef, ReviewDecision?) -> Unit) {
    val fromCur = remember(card) { currencyFor(card.fromCurrency, card.fromMinorUnits) }
    val toCur = remember(card) { currencyFor(card.toCurrency, card.toMinorUnits) }
    var fromText by remember(card) {
        mutableStateOf(AmountInput.fromMinor(Minor(card.fromAmountMinor ?: 0L), fromCur).displayText())
    }
    var toText by remember(card) {
        mutableStateOf(AmountInput.fromMinor(Minor(card.toAmountMinor ?: 0L), toCur).displayText())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = fromText,
            onValueChange = { fromText = it },
            label = { Text(stringResource(R.string.import_review_exact_from) + " " + card.fromCurrency.orEmpty()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = toText,
            onValueChange = { toText = it },
            label = { Text(stringResource(R.string.import_review_exact_to) + " " + card.toCurrency.orEmpty()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    val fromMinor = AmountInput.fromText(fromText, fromCur).toMinorOrNull(fromCur)?.raw
    val toMinor = AmountInput.fromText(toText, toCur).toMinorOrNull(toCur)?.raw
    OutlinedButton(
        onClick = {
            if (fromMinor != null && toMinor != null && fromMinor > 0 && toMinor > 0) {
                onDecision(card.ref, ReviewDecision.ExactAmounts(fromMinor, toMinor))
            }
        },
    ) { Text(stringResource(R.string.import_review_exact_apply)) }
}

/** R6.3: текст ошибки импорта из [ImportFailureReason] — `Raw` уже готов, `Localized` строим здесь. */
@Composable
private fun importFailureText(reason: ImportFailureReason): String = when (reason) {
    is ImportFailureReason.Raw -> reason.text
    is ImportFailureReason.Localized -> stringResource(reason.resId, *reason.args.toTypedArray())
}

/** R6.3: почему строка CSV не разобралась — текст строит экран, `core/data` отдаёт только структуру. */
@Composable
private fun monefyRowIssueText(issue: MonefyRowIssue): String = when (issue) {
    is MonefyRowIssue.WrongFieldCount -> stringResource(R.string.import_error_wrong_field_count, issue.expected, issue.actual)
    is MonefyRowIssue.UnparseableDate -> stringResource(R.string.import_error_unparseable_date, issue.raw)
    is MonefyRowIssue.UnparseableAmount -> stringResource(R.string.import_error_unparseable_amount, issue.raw, issue.detail.orEmpty())
    is MonefyRowIssue.UnparseableConvertedAmount ->
        stringResource(R.string.import_error_unparseable_converted_amount, issue.raw, issue.detail.orEmpty())
}

/** R6.3: текст позиции NEEDS_REVIEW — планировщик отдаёт только причину и структурные поля. */
@Composable
private fun reviewCardMessage(card: ReviewCard): String = when (card.reason) {
    ReviewReason.ACCOUNT_CURRENCY_CONFLICT -> stringResource(
        R.string.import_review_account_currency_conflict,
        card.account.orEmpty(),
        card.currencyChoices.getOrElse(0) { "" },
        card.currencyChoices.getOrElse(1) { "" },
    )
    ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH -> stringResource(
        R.string.import_review_existing_account_mismatch,
        card.account.orEmpty(),
        card.existingCurrency.orEmpty(),
        card.currencyChoices.firstOrNull().orEmpty(),
    )
    ReviewReason.AMBIGUOUS_PAIRING -> stringResource(
        R.string.import_review_ambiguous_pairing,
        card.pairCount,
        card.transferFromAccount.orEmpty(),
        card.transferToAccount.orEmpty(),
        card.transferDate.orEmpty(),
    )
    ReviewReason.ANOMALOUS_CURRENCY -> stringResource(
        R.string.import_review_anomalous_currency,
        card.transferFromAccount.orEmpty(),
        card.transferToAccount.orEmpty(),
        card.transferDate.orEmpty(),
    )
    ReviewReason.EXCESS_PRECISION -> stringResource(
        R.string.import_review_excess_precision,
        card.transferFromAccount.orEmpty(),
        card.transferToAccount.orEmpty(),
        card.transferDate.orEmpty(),
    )
}

private fun currencyFor(code: String?, minorUnits: Int): Currency =
    Currency(CurrencyCode(code ?: "XXX"), minorUnits = minorUnits, displayScale = minorUnits, symbol = code ?: "")
