package com.corriente.app.ui.fxreport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer

/** T5.4: список межвалютных сделок с выведенным курсом и графиком курса по времени. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxReportScreen(
    onBack: () -> Unit,
    viewModel: FxReportViewModel = viewModel(
        factory = with(corrienteContainer()) { FxReportViewModel.factory(txnRepository, currencyRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fx_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loaded && state.pairs.isEmpty()) {
            Text(
                stringResource(R.string.fx_report_empty),
                Modifier.padding(padding).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (state.conversionCosts.isNotEmpty()) {
                item(key = "conversion-cost-header") {
                    Text(
                        stringResource(R.string.fx_conversion_cost_title),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.conversionCosts, key = { "cost-${it.title}" }) { cost ->
                    ConversionCostRow(cost)
                    HorizontalDivider()
                }
            }
            state.pairs.forEach { pair ->
                item(key = "h-${pair.title}") {
                    Text(
                        pair.title,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (pair.deals.size >= 2) {
                    item(key = "c-${pair.title}") { RateChart(pair.deals) }
                }
                items(pair.deals.size) { i ->
                    val deal = pair.deals[i]
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(deal.dateText, style = MaterialTheme.typography.bodySmall)
                            Text(deal.rateLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "${deal.fromText} → ${deal.toText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * R3.4: строка «во что обошлись конвертации в этом году» по одной паре валют. Меньше трёх
 * сделок по паре — сравнивать курс не с чем (ADR-013), показываем текст «недостаточно
 * данных», а не 0 — ноль читался бы как «конвертации ничего не стоили».
 */
@Composable
private fun ConversionCostRow(cost: ConversionCostUi) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cost.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (cost.insufficientData) {
                    stringResource(R.string.fx_conversion_cost_insufficient_data)
                } else {
                    stringResource(R.string.fx_conversion_cost_amount, cost.amountText.orEmpty())
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.fx_conversion_cost_deal_count, cost.dealCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateChart(deals: List<FxDealUi>) {
    val values = deals.map { it.rateMicros }
    val min = values.min()
    val max = values.max()
    val span = (max - min).coerceAtLeast(1L)
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(
        Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val n = values.size
        val stepX = if (n > 1) size.width / (n - 1) else size.width
        val points = values.mapIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min).toDouble() / span.toDouble() * size.height).toFloat()
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 4f)
        }
        points.forEach { drawCircle(lineColor, radius = 5f, center = it) }
    }
}
