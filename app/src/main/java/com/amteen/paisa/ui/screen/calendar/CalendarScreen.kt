package com.amteen.paisa.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.model.TransactionTotals
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.usecase.CalendarDay
import com.amteen.paisa.domain.usecase.GetMonthCalendarUseCase
import com.amteen.paisa.domain.usecase.MonthCalendar
import com.amteen.paisa.ui.charts.ShareBar
import com.amteen.paisa.ui.components.AmountText
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.ErrorState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.NetAmountText
import com.amteen.paisa.ui.components.TransactionRow
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The calendar.
 *
 * Renders [CalendarUiState] and nothing else: every per-day figure was derived by
 * `GetMonthCalendarUseCase`, so this file sums nothing and converts nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onEvent: (CalendarEvent) -> Unit,
    onTransactionClick: (String) -> Unit,
    onAddTransaction: (LocalDate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_calendar)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            state.isLoading -> LoadingState(modifier = content)

            state.error != null -> ErrorState(
                modifier = content,
                title = stringResource(R.string.calendar_error_title),
                message = state.error,
                retryLabel = stringResource(R.string.action_retry),
                onRetry = { onEvent(CalendarEvent.Retry) },
            )

            else -> Column(
                modifier = content.verticalScroll(rememberScrollState()),
            ) {
                MonthBar(month = state.month, onEvent = onEvent)

                val calendar = state.calendar
                if (calendar == null || state.isEmpty) {
                    // The grid still renders behind the empty state on a quiet month:
                    // the point of a calendar is the shape of the month, and hiding it
                    // would also hide the day the user wants to record against.
                    if (calendar != null) {
                        MonthGrid(
                            calendar = calendar,
                            onDayClick = { onEvent(CalendarEvent.DaySelected(it)) },
                        )
                    }
                    EmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(R.string.calendar_empty_title),
                        message = stringResource(R.string.calendar_empty_message),
                    )
                } else {
                    MonthSummaryCard(
                        calendar = calendar,
                        onBusiestDayClick = { onEvent(CalendarEvent.DaySelected(it)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    MonthGrid(
                        calendar = calendar,
                        onDayClick = { onEvent(CalendarEvent.DaySelected(it)) },
                    )
                    CurrencyCaption(calendar = calendar)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val day = state.selectedDay
    val calendar = state.calendar
    if (day != null && calendar != null) {
        DayDetailSheet(
            day = day,
            currency = calendar.baseCurrency,
            today = calendar.today,
            onTransactionClick = onTransactionClick,
            onAddTransaction = { onAddTransaction(day.date) },
            onDismiss = { onEvent(CalendarEvent.DayDismissed) },
        )
    }
}

// -- Month navigation -------------------------------------------------------

@Composable
private fun MonthBar(
    month: YearMonth,
    onEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(CalendarEvent.PreviousMonth) }) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(
            text = DateFormatters.month(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onEvent(CalendarEvent.NextMonth) }) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }

    if (month != YearMonth.now()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { onEvent(CalendarEvent.ThisMonth) }) {
                Text(stringResource(R.string.calendar_this_month))
            }
        }
    }
}

// -- Summary ----------------------------------------------------------------

/**
 * The month's figures, and the spoken alternative for the grid below it.
 *
 * A 42-cell grid where every cell has to be toured to learn anything is technically
 * accessible and practically unusable, so the shape of the month is stated here in
 * one focus stop — totals, how many days had money on them, and the busiest day as a
 * button that jumps straight to it. That shortcut is worth having sighted too.
 */
@Composable
private fun MonthSummaryCard(
    calendar: MonthCalendar,
    onBusiestDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = calendar.baseCurrency
    val totals = calendar.totals

    val overview = if (calendar.hasAnyTransactions) {
        stringResource(
            R.string.calendar_overview_spoken,
            DateFormatters.month(calendar.month),
            MoneyFormatter.format(totals.expense, currency),
            MoneyFormatter.format(totals.income, currency),
            calendar.activeDayCount,
            calendar.daysInMonth,
        )
    } else {
        stringResource(
            R.string.calendar_overview_spoken_quiet,
            DateFormatters.month(calendar.month),
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                // Spoken as one sentence, and it is the sentence that stands in for
                // the grid. The busiest-day button below keeps its own label.
                modifier = Modifier.clearAndSetSemantics { contentDescription = overview },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.calendar_month_summary),
                    style = MaterialTheme.typography.titleSmall,
                )
                NetAmountText(
                    money = totals.net,
                    currency = currency,
                    style = AmountTextStyles.Large,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure(
                        label = stringResource(R.string.home_spent),
                        money = totals.expense,
                        currency = currency,
                        type = TransactionType.EXPENSE,
                        modifier = Modifier.weight(1f),
                    )
                    Figure(
                        label = stringResource(R.string.home_income),
                        money = totals.income,
                        currency = currency,
                        type = TransactionType.INCOME,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.calendar_active_days,
                        calendar.activeDayCount,
                        calendar.daysInMonth,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            calendar.busiestDay?.let { busiest ->
                HorizontalDivider()
                BusiestDayRow(
                    day = busiest,
                    today = calendar.today,
                    currency = currency,
                    onClick = { onBusiestDayClick(busiest.date) },
                )
            }
        }
    }
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

@Composable
private fun BusiestDayRow(
    day: CalendarDay,
    today: LocalDate,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayLabel = DateFormatters.date(day.date, today)
    val amount = MoneyFormatter.format(day.expense, currency)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = "$dayLabel. $amount spent. Opens the day."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.calendar_busiest_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.calendar_busiest_day_value, dayLabel, amount),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrencyCaption(calendar: MonthCalendar, modifier: Modifier = Modifier) {
    // A cell is about 48dp wide, so its figures drop the currency symbol. This is the
    // one line that says what unit the grid is in — without it, "1.2K" is "1.2K of
    // what?" on the only screen in the app that shows a bare number.
    Text(
        text = stringResource(R.string.calendar_figures_in, calendar.baseCurrency.symbol),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// -- The grid ---------------------------------------------------------------

@Composable
private fun MonthGrid(
    calendar: MonthCalendar,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        WeekdayHeader(weekdays = calendar.weekdays)

        calendar.weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // One traversal group per week, so TalkBack reads a row at a time
                    // and a swipe past the group skips a whole week rather than
                    // stepping through it a day at a time.
                    .semantics { isTraversalGroup = true },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        today = calendar.today,
                        currency = calendar.baseCurrency,
                        peakExpenseMinor = calendar.peakExpenseMinor,
                        onClick = { onDayClick(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun WeekdayHeader(weekdays: List<DayOfWeek>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            // The column headings repeat what every cell already says out loud, so
            // they are decoration to a screen reader.
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        weekdays.forEach { weekday ->
            Text(
                text = weekday.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One day.
 *
 * Income and expense both have to fit in about 48dp, so the figures are
 * [MoneyFormatter.formatCompact] without a symbol — the caption under the grid names
 * the currency. Each carries an explicit sign: at this size colour is doing most of
 * the work of separating the two, and red/green is never allowed to be the only
 * signal.
 *
 * Under them is a bar scaled against the month's busiest day, so the shape of a
 * month is legible at a glance without reading a single figure — and it encodes
 * spending as *length*, which survives both colour blindness and a greyscale screen.
 */
@Composable
private fun DayCell(
    day: CalendarDay,
    today: LocalDate,
    currency: Currency,
    peakExpenseMinor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.expenseColors

    // A quiet day from a neighbouring month carries nothing at all: it is there to
    // square off the row. Leaving it out of the semantics tree keeps up to twelve
    // empty stops out of a screen reader user's way.
    val decorative = !day.inMonth && !day.hasActivity

    val dayLabel = DateFormatters.date(day.date, today)
    val figures = when {
        day.hasExpense && day.hasIncome -> stringResource(
            R.string.calendar_day_both,
            dayLabel,
            MoneyFormatter.format(day.expense, currency),
            MoneyFormatter.format(day.income, currency),
        )
        day.hasExpense -> stringResource(
            R.string.calendar_day_spent,
            dayLabel,
            MoneyFormatter.format(day.expense, currency),
        )
        day.hasIncome -> stringResource(
            R.string.calendar_day_received,
            dayLabel,
            MoneyFormatter.format(day.income, currency),
        )
        else -> stringResource(R.string.calendar_day_nothing, dayLabel)
    }
    val spoken = if (day.inMonth) {
        figures
    } else {
        stringResource(R.string.calendar_day_outside_month, dayLabel, figures)
    }

    val cell = Modifier
        .height(CellHeight)
        .then(
            if (decorative) {
                Modifier.clearAndSetSemantics { }
            } else {
                Modifier
                    .clickable(onClick = onClick)
                    // A cell speaks as one unit. Reading the number, then the
                    // expense, then the income as three stops turns a month into
                    // ninety swipes.
                    .clearAndSetSemantics { contentDescription = spoken }
            },
        )

    Box(modifier = modifier.then(cell), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DayNumber(day = day, dimmed = !day.inMonth)

            Spacer(Modifier.height(2.dp))

            if (day.hasExpense) {
                CellFigure(
                    // Negated so the formatter prints the minus sign rather than the
                    // cell assembling one — money strings come from one place only.
                    text = MoneyFormatter.formatCompact(
                        money = -day.expense,
                        currency = currency,
                        withSymbol = false,
                    ),
                    color = colors.expense,
                    dimmed = !day.inMonth,
                )
            }
            if (day.hasIncome) {
                CellFigure(
                    text = MoneyFormatter.formatCompact(
                        money = day.income,
                        currency = currency,
                        withSymbol = false,
                        signed = true,
                    ),
                    color = colors.income,
                    dimmed = !day.inMonth,
                )
            }

            Spacer(Modifier.weight(1f))

            if (day.hasExpense && peakExpenseMinor > 0L) {
                ShareBar(
                    fraction = (day.expenseMinor.toDouble() / peakExpenseMinor.toDouble())
                        .coerceIn(0.0, 1.0)
                        .toFloat(),
                    color = if (day.inMonth) {
                        colors.expense
                    } else {
                        colors.expense.copy(alpha = DimmedAlpha)
                    },
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun DayNumber(day: CalendarDay, dimmed: Boolean, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val today = day.isToday

    Box(
        modifier = modifier
            .size(22.dp)
            .then(
                when {
                    // Today is marked by a filled disc, and the selected month's days
                    // by weight — not by colour alone.
                    today -> Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    day.hasActivity && day.inMonth -> Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (today || day.hasActivity) FontWeight.Bold else FontWeight.Normal,
            color = when {
                today -> MaterialTheme.colorScheme.onPrimary
                dimmed -> onSurface.copy(alpha = DimmedAlpha)
                else -> onSurface
            },
        )
    }
}

@Composable
private fun CellFigure(
    text: String,
    color: Color,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontFeatureSettings = "tnum",
        ),
        color = if (dimmed) color.copy(alpha = DimmedAlpha) else color,
        maxLines = 1,
        softWrap = false,
        // Never silently truncate an amount: "-1.2…" is visibly incomplete, whereas
        // a clipped "-1.2" reads as a smaller legitimate figure.
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

// -- Day detail -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    day: CalendarDay,
    currency: Currency,
    today: LocalDate,
    onTransactionClick: (String) -> Unit,
    onAddTransaction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = DateFormatters.date(day.date, today),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = DateFormatters.fullDate(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (day.hasActivity) {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (day.hasExpense) {
                            Figure(
                                label = stringResource(R.string.home_spent),
                                money = day.expense,
                                currency = currency,
                                type = TransactionType.EXPENSE,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (day.hasIncome) {
                            Figure(
                                label = stringResource(R.string.home_income),
                                money = day.income,
                                currency = currency,
                                type = TransactionType.INCOME,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.calendar_net),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            NetAmountText(money = day.net, currency = currency)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.calendar_day_entries,
                            day.count,
                            day.count,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (day.hasActivity) {
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(day.items, key = { it.id }) { details ->
                        TransactionRow(
                            details = details,
                            onClick = { onTransactionClick(details.id) },
                            showTime = true,
                        )
                    }
                }
                HorizontalDivider()
            } else {
                Text(
                    text = stringResource(R.string.calendar_day_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pre-dated to the day being looked at, which is the whole point of
                // reaching the add screen from here rather than from the FAB.
                TextButton(onClick = onAddTransaction) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.calendar_day_add_expense))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.calendar_day_close))
                }
            }
        }
    }
}

private val CellHeight = 62.dp

/** Leading and trailing days are present but not competing for attention. */
private const val DimmedAlpha = 0.38f

// -- Previews ---------------------------------------------------------------

@Preview(name = "Calendar", showBackground = true, heightDp = 900)
@Composable
private fun CalendarPreview() {
    PaisaTheme {
        CalendarScreen(
            state = previewState(),
            onEvent = {},
            onTransactionClick = {},
            onAddTransaction = {},
            onBack = {},
        )
    }
}

@Preview(name = "Calendar · dark", showBackground = true, heightDp = 900, uiMode = 32)
@Composable
private fun CalendarDarkPreview() {
    PaisaTheme {
        CalendarScreen(
            state = previewState(),
            onEvent = {},
            onTransactionClick = {},
            onAddTransaction = {},
            onBack = {},
        )
    }
}

@Preview(name = "Calendar · quiet month", showBackground = true, heightDp = 900)
@Composable
private fun CalendarEmptyPreview() {
    PaisaTheme {
        CalendarScreen(
            state = previewState(withSpending = false),
            onEvent = {},
            onTransactionClick = {},
            onAddTransaction = {},
            onBack = {},
        )
    }
}

private fun previewState(withSpending: Boolean = true): CalendarUiState {
    val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    val month = YearMonth.of(2026, 9)
    val today = LocalDate.of(2026, 9, 15)
    val food = Category(
        id = "cat-food",
        name = "Food & Drink",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0xFFE07A5F.toInt(),
    )

    fun details(id: String, amountMinor: Long, date: LocalDate) = TransactionDetails(
        transaction = Transaction(
            id = id,
            type = TransactionType.EXPENSE,
            amountMinor = amountMinor,
            currencyCode = "PKR",
            categoryId = food.id,
            description = "Groceries",
            date = date,
            time = LocalTime.of(13, 20),
        ),
        category = food,
        subcategory = null,
        paymentMethod = null,
        currency = pkr,
    )

    // Deliberately uneven, so the bars have a shape and the peak means something.
    val spendByDay = if (withSpending) {
        mapOf(
            2 to 45_000L, 3 to 120_000L, 5 to 380_000L, 6 to 62_000L,
            9 to 210_000L, 11 to 95_000L, 12 to 1_240_000L, 14 to 33_000L,
            15 to 85_000L,
        )
    } else {
        emptyMap()
    }
    val incomeByDay = if (withSpending) mapOf(1 to 4_500_000L) else emptyMap()

    val range = GetMonthCalendarUseCase.gridRange(month, DayOfWeek.MONDAY)
    val weeks = (0 until range.dayCount)
        .map { range.start.plusDays(it.toLong()) }
        .map { date ->
            val inMonth = YearMonth.from(date) == month
            val expense = if (inMonth) spendByDay[date.dayOfMonth] ?: 0L else 0L
            val income = if (inMonth) incomeByDay[date.dayOfMonth] ?: 0L else 0L
            CalendarDay(
                date = date,
                inMonth = inMonth,
                isToday = date == today,
                incomeMinor = income,
                expenseMinor = expense,
                currencyCode = "PKR",
                items = if (expense > 0L) listOf(details("t-$date", expense, date)) else emptyList(),
            )
        }
        .chunked(GetMonthCalendarUseCase.COLUMNS)

    val inMonth = weeks.flatten().filter { it.inMonth }
    val busiest = inMonth.filter { it.hasExpense }.maxByOrNull { it.expenseMinor }

    val calendar = MonthCalendar(
        month = month,
        today = today,
        weekdays = GetMonthCalendarUseCase.weekdayOrder(DayOfWeek.MONDAY),
        weeks = weeks,
        baseCurrency = pkr,
        totals = TransactionTotals(
            income = Money(inMonth.sumOf { it.incomeMinor }, "PKR"),
            expense = Money(inMonth.sumOf { it.expenseMinor }, "PKR"),
            count = inMonth.sumOf { it.count },
        ),
        peakExpenseMinor = busiest?.expenseMinor ?: 0L,
        busiestDay = busiest,
        activeDayCount = inMonth.count { it.hasActivity },
    )

    return CalendarUiState(
        isLoading = false,
        month = month,
        calendar = calendar,
    )
}
