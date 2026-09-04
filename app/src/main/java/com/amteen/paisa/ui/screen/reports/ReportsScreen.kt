package com.amteen.paisa.ui.screen.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.amteen.paisa.R
import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.core.time.PeriodFilter
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.TransactionTotals
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.usecase.CategorySlice
import com.amteen.paisa.domain.usecase.DailyPoint
import com.amteen.paisa.domain.usecase.MonthlyPoint
import com.amteen.paisa.domain.usecase.Report
import com.amteen.paisa.ui.charts.CategoryDonut
import com.amteen.paisa.ui.charts.DailyExpenseBars
import com.amteen.paisa.ui.charts.IncomeExpenseTrend
import com.amteen.paisa.ui.charts.LabelledShareRow
import com.amteen.paisa.ui.components.AmountText
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.ErrorState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.NetAmountText
import com.amteen.paisa.ui.components.TransactionRow
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The reports screen.
 *
 * Renders [ReportsUiState] and nothing else: every figure and every series was
 * derived by `BuildReportUseCase`, so this file sums nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onEvent: (ReportsEvent) -> Unit,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_reports)) }) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            state.isLoading -> LoadingState(modifier = content)

            state.error != null -> ErrorState(
                modifier = content,
                title = stringResource(R.string.reports_error_title),
                message = state.error,
                retryLabel = stringResource(R.string.action_retry),
                onRetry = { onEvent(ReportsEvent.Retry) },
            )

            else -> LazyColumn(
                modifier = content,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "period") {
                    PeriodRow(selected = state.period, onEvent = onEvent)
                }

                val report = state.report
                if (report == null || state.isEmpty) {
                    item(key = "empty") {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp),
                            icon = Icons.Filled.BarChart,
                            title = stringResource(R.string.reports_empty_title),
                            message = stringResource(R.string.reports_empty_message),
                        )
                    }
                } else {
                    item(key = "overview") { OverviewCard(report = report) }

                    item(key = "categories") {
                        CategorySection(report = report, onEvent = onEvent)
                    }

                    item(key = "daily") { DailySection(report = report) }

                    if (report.monthlySeries.size >= 2) {
                        item(key = "trend") { TrendSection(report = report) }
                    }

                    if (report.topExpenses.isNotEmpty()) {
                        item(key = "top-expenses-header") {
                            SectionTitle(stringResource(R.string.reports_top_expenses))
                        }
                        items(report.topExpenses, key = { "expense-${it.id}" }) { details ->
                            TransactionRow(
                                details = details,
                                onClick = { onTransactionClick(details.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showRangePicker) {
        CustomRangeDialog(
            onDismiss = { onEvent(ReportsEvent.DismissRangePicker) },
            onConfirm = { onEvent(ReportsEvent.CustomRangeSelected(it)) },
        )
    }
}

// -- Period -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodRow(
    selected: PeriodFilter,
    onEvent: (ReportsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The label comes from PeriodFilter itself, so reports and history can
            // never end up calling the same period two different things.
            listOf(
                PeriodFilter.ThisMonth,
                PeriodFilter.LastMonth,
                PeriodFilter.ThisWeek,
                PeriodFilter.ThisYear,
                PeriodFilter.AllTime,
            ).forEach { period ->
                FilterChip(
                    selected = selected == period,
                    onClick = { onEvent(ReportsEvent.PeriodSelected(period)) },
                    label = { Text(period.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            FilterChip(
                selected = selected is PeriodFilter.Custom,
                onClick = { onEvent(ReportsEvent.OpenRangePicker) },
                label = {
                    Text(
                        if (selected is PeriodFilter.Custom) {
                            selected.label
                        } else {
                            stringResource(R.string.reports_custom_range)
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit,
) {
    val pickerState = rememberDateRangePickerState()

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                DateRangePicker(
                    state = pickerState,
                    modifier = Modifier.heightIn(max = 520.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        // Both ends are required: a half-chosen range has no
                        // meaning, and defaulting the missing end to today would
                        // silently report a period the user did not ask for.
                        enabled = pickerState.selectedStartDateMillis != null &&
                            pickerState.selectedEndDateMillis != null,
                        onClick = {
                            val start = pickerState.selectedStartDateMillis
                            val end = pickerState.selectedEndDateMillis
                            if (start != null && end != null) {
                                onConfirm(DateRange(start.toLocalDate(), end.toLocalDate()))
                            }
                        },
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

/**
 * The picker hands back UTC midnight millis, so the date has to be read back in UTC.
 * Reading it in the device zone shifts the boundary by a day west of Greenwich.
 */
private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

// -- Overview ---------------------------------------------------------------

@Composable
private fun OverviewCard(report: Report, modifier: Modifier = Modifier) {
    val currency = report.currency
    val totals = report.totals

    val spoken = buildString {
        append(report.label)
        append(". Spent ")
        append(MoneyFormatter.format(totals.expense, currency))
        append(", received ")
        append(MoneyFormatter.format(totals.income, currency))
        append(". Net ")
        append(MoneyFormatter.format(totals.net, currency))
        append(". Averaging ")
        append(MoneyFormatter.format(report.averageExpensePerDay, currency))
        append(" a day over ")
        append(report.dayCount)
        append(" days.")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                // The card is one figure read four ways; four separate stops would
                // make a screen reader user reassemble the sentence themselves.
                .clearAndSetSemantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = report.label, style = MaterialTheme.typography.titleSmall)

            NetAmountText(
                money = totals.net,
                currency = currency,
                style = AmountTextStyles.Hero,
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Figure(
                    label = stringResource(R.string.reports_spent),
                    money = totals.expense,
                    currency = currency,
                    type = TransactionType.EXPENSE,
                    modifier = Modifier.weight(1f),
                )
                Figure(
                    label = stringResource(R.string.reports_income),
                    money = totals.income,
                    currency = currency,
                    type = TransactionType.INCOME,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reports_per_day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = MoneyFormatter.format(report.averageExpensePerDay, currency),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.reports_over_days,
                            report.dayCount,
                            report.dayCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.reports_entry_count,
                            totals.count,
                            totals.count,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    report.busiestDay?.let { busiest ->
                        Text(
                            text = stringResource(R.string.reports_busiest),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${DateFormatters.compactDate(busiest.date)} · " +
                                MoneyFormatter.formatCompact(busiest.expense, currency),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            ComparisonLine(report = report)
        }
    }
}

@Composable
private fun ComparisonLine(report: Report, modifier: Modifier = Modifier) {
    val percent = report.expenseChangePercent
    val text = when {
        report.previousExpenseMinor == null -> null
        percent == null -> stringResource(R.string.reports_compare_none)
        percent.roundToInt() == 0 -> stringResource(
            R.string.reports_compare_same,
            report.label.lowercase(),
        )
        percent > 0 -> stringResource(
            R.string.reports_compare_up,
            stringResource(R.string.home_percent, abs(percent).roundToInt()),
            report.label.lowercase(),
        )
        else -> stringResource(
            R.string.reports_compare_down,
            stringResource(R.string.home_percent, abs(percent).roundToInt()),
            report.label.lowercase(),
        )
    } ?: return

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Figure(
    label: String,
    money: Money,
    currency: Currency,
    type: TransactionType,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountText(money = money, currency = currency, type = type)
    }
}

// -- Categories and the drill-down ------------------------------------------

@Composable
private fun CategorySection(
    report: Report,
    onEvent: (ReportsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (report.categories.isEmpty()) return
    val palette = MaterialTheme.expenseColors.chartSeries

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.reports_by_category))

        CategoryDonut(
            slices = report.categories,
            currency = report.currency,
            total = MoneyFormatter.formatCompact(report.totals.expense, report.currency),
            selectedCategoryId = report.selectedCategoryId,
        )

        Text(
            text = stringResource(R.string.reports_drill_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        report.categories.forEachIndexed { index, slice ->
            val selected = slice.categoryId == report.selectedCategoryId
            val spoken = "${slice.name}, " +
                MoneyFormatter.format(slice.amount, report.currency) +
                ", ${(slice.share * 100).roundToInt()} percent. " +
                if (selected) "Showing its breakdown." else "Tap to break it down."

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(ReportsEvent.CategoryToggled(slice.categoryId)) }
                    .heightIn(min = 48.dp)
                    .clearAndSetSemantics { contentDescription = spoken },
            ) {
                LabelledShareRow(
                    label = slice.name,
                    value = MoneyFormatter.format(slice.amount, report.currency),
                    share = slice.share,
                    color = palette[index % palette.size],
                    supporting = pluralStringResource(
                        R.plurals.reports_entry_count,
                        slice.count,
                        slice.count,
                    ),
                )
            }

            if (selected) {
                SubcategoryBreakdown(
                    report = report,
                    color = palette[index % palette.size],
                    onClose = { onEvent(ReportsEvent.CategoryToggled(slice.categoryId)) },
                )
            }
        }
    }
}

@Composable
private fun SubcategoryBreakdown(
    report: Report,
    color: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = report.selectedCategory?.name ?: return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reports_subcategories_of, name),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.reports_close_drilldown),
                    )
                }
            }

            if (report.subcategories.isEmpty()) {
                Text(
                    text = stringResource(R.string.category_no_subcategories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.subcategories.forEach { slice ->
                    LabelledShareRow(
                        label = slice.name,
                        value = MoneyFormatter.format(slice.amount, report.currency),
                        share = slice.share,
                        color = color,
                    )
                }
            }
        }
    }
}

// -- Charts -----------------------------------------------------------------

@Composable
private fun DailySection(report: Report, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.reports_daily))
        if (report.dailySeries.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_daily_too_long),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DailyExpenseBars(days = report.dailySeries, currency = report.currency)
        }
    }
}

@Composable
private fun TrendSection(report: Report, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.reports_trend))
        IncomeExpenseTrend(months = report.monthlySeries, currency = report.currency)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 4.dp),
    )
}

// -- Previews ---------------------------------------------------------------

@Preview(name = "Reports", showBackground = true, heightDp = 1400)
@Composable
private fun ReportsPreview() {
    PaisaTheme {
        ReportsScreen(state = previewState(), onEvent = {}, onTransactionClick = {})
    }
}

@Preview(name = "Reports · dark", showBackground = true, heightDp = 1400, uiMode = 32)
@Composable
private fun ReportsDarkPreview() {
    PaisaTheme {
        ReportsScreen(state = previewState(), onEvent = {}, onTransactionClick = {})
    }
}

@Preview(name = "Reports · empty", showBackground = true, heightDp = 700)
@Composable
private fun ReportsEmptyPreview() {
    PaisaTheme {
        ReportsScreen(
            state = ReportsUiState(isLoading = false, report = previewState().report?.copy(
                totals = TransactionTotals.empty("PKR"),
                categories = emptyList(),
                dailySeries = emptyList(),
                topExpenses = emptyList(),
            )),
            onEvent = {},
            onTransactionClick = {},
        )
    }
}

private fun previewState(): ReportsUiState {
    val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    val month = YearMonth.of(2026, 9)

    fun slice(id: String, name: String, amount: Long, colour: Int, share: Float, count: Int) =
        CategorySlice(
            categoryId = id,
            name = name,
            colorArgb = colour,
            iconKey = "restaurant",
            amountMinor = amount,
            currencyCode = "PKR",
            share = share,
            count = count,
        )

    val categories = listOf(
        slice("c1", "Food & Drink", 1_840_000, 0xFFE07A5F.toInt(), 0.42f, 34),
        slice("c2", "Transport", 920_000, 0xFF3F6BB5.toInt(), 0.21f, 18),
        slice("c3", "Shopping", 660_000, 0xFF7A5EA8.toInt(), 0.15f, 7),
        slice("c4", "Bills", 520_000, 0xFF4E8F8B.toInt(), 0.12f, 5),
        slice("c5", "Health", 440_000, 0xFFB5476B.toInt(), 0.10f, 3),
    )

    val daily = (1..30).map { day ->
        val date = month.atDay(day)
        DailyPoint(
            date = date,
            expenseMinor = when (day % 7) {
                0 -> 340_000L
                3 -> 120_000L
                5 -> 210_000L
                else -> 45_000L
            },
            incomeMinor = if (day == 1) 4_500_000L else 0L,
            currencyCode = "PKR",
        )
    }

    val monthly = (0..5).map { offset ->
        val m = month.minusMonths((5 - offset).toLong())
        MonthlyPoint(
            month = m,
            expenseMinor = 3_600_000L + offset * 180_000L,
            incomeMinor = 4_500_000L,
            currencyCode = "PKR",
        )
    }

    return ReportsUiState(
        isLoading = false,
        period = PeriodFilter.ThisMonth,
        report = Report(
            period = PeriodFilter.ThisMonth,
            range = DateRange.of(month),
            label = "This month",
            currency = pkr,
            totals = TransactionTotals(
                income = Money(4_500_000, "PKR"),
                expense = Money(4_380_000, "PKR"),
                mixedCurrency = false,
                count = 67,
            ),
            dayCount = 30,
            averageExpensePerDayMinor = 146_000,
            categories = categories,
            selectedCategoryId = null,
            subcategories = emptyList(),
            dailySeries = daily,
            monthlySeries = monthly,
            topExpenses = emptyList(),
            busiestDay = daily.maxByOrNull { it.expenseMinor },
            previousExpenseMinor = 3_960_000,
        ),
    )
}
