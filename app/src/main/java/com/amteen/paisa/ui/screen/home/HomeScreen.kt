package com.amteen.paisa.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetProgress
import com.amteen.paisa.domain.model.BudgetStatus
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.model.TransactionTotals
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.usecase.BudgetSummary
import com.amteen.paisa.domain.usecase.CategorySpend
import com.amteen.paisa.domain.usecase.DailySpend
import com.amteen.paisa.domain.usecase.DashboardSummary
import com.amteen.paisa.ui.charts.DailySpendBars
import com.amteen.paisa.ui.charts.ShareBar
import com.amteen.paisa.ui.components.AmountText
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.ErrorState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.NetAmountText
import com.amteen.paisa.ui.components.TransactionRow
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The dashboard.
 *
 * Renders [HomeUiState] and nothing else — every figure on this screen was derived
 * by `GetDashboardSummaryUseCase`, so there is no arithmetic anywhere below. See
 * CLAUDE.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onCategories: () -> Unit,
    onBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        state.summary?.let {
                            Text(
                                text = DateFormatters.month(it.month),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                message = stringResource(R.string.home_error_hint),
                title = stringResource(R.string.home_error_title),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = { onEvent(HomeEvent.Retry) },
                modifier = content,
            )

            state.isEmpty -> EmptyState(
                icon = Icons.Filled.Insights,
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
                actionLabel = stringResource(R.string.home_empty_action),
                onAction = onAddExpense,
                modifier = content,
            )

            state.summary != null -> DashboardContent(
                summary = state.summary,
                onAddExpense = onAddExpense,
                onAddIncome = onAddIncome,
                onSeeAllTransactions = onSeeAllTransactions,
                onTransactionClick = onTransactionClick,
                onCategories = onCategories,
                onBudgets = onBudgets,
                modifier = content,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onCategories: () -> Unit,
    onBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            // Room for the floating action button, which otherwise covers the last
            // row of whatever section ends the list.
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "balance") {
            BalanceCard(summary = summary)
        }

        item(key = "quick-stats") {
            QuickStatsRow(summary = summary)
        }

        item(key = "week") {
            WeekCard(summary = summary)
        }

        if (summary.budgets.isNotEmpty()) {
            item(key = "budgets-header") {
                SectionHeader(
                    title = stringResource(R.string.home_budgets_title),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = onBudgets,
                )
            }
            items(
                items = summary.budgets.take(MAX_BUDGET_ROWS),
                key = { "budget-${it.id}" },
            ) { budget ->
                BudgetRow(summary = budget)
            }
        }

        if (summary.topCategories.isNotEmpty()) {
            item(key = "categories-header") {
                SectionHeader(
                    title = stringResource(R.string.home_top_categories_title),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = onCategories,
                )
            }
            item(key = "categories") {
                Card(colors = cardColors()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        summary.topCategories.forEach { spend ->
                            CategorySpendRow(
                                spend = spend,
                                currency = summary.baseCurrency,
                            )
                        }
                    }
                }
            }
        }

        if (summary.recent.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader(
                    title = stringResource(R.string.home_recent_title),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = onSeeAllTransactions,
                )
            }
            item(key = "recent") {
                Card(colors = cardColors()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        summary.recent.forEach { details ->
                            TransactionRow(
                                details = details,
                                onClick = { onTransactionClick(details.id) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "quick-actions-header") {
            SectionHeader(title = stringResource(R.string.home_quick_actions_title))
        }
        item(key = "quick-actions") {
            QuickActions(
                onAddExpense = onAddExpense,
                onAddIncome = onAddIncome,
                onSeeAllTransactions = onSeeAllTransactions,
                onCategories = onCategories,
            )
        }
    }
}

// -- Balance ----------------------------------------------------------------

@Composable
private fun BalanceCard(summary: DashboardSummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_net_this_month),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The net figure gets the full width to itself. A salary of
            // Rs. 150,000.00 beside anything else is how Phase 3's amounts ended up
            // silently clipped to "+Rs.".
            NetAmountText(
                money = summary.totals.net,
                currency = summary.baseCurrency,
                style = AmountTextStyles.Hero,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DirectionalTotal(
                    label = stringResource(R.string.home_income),
                    money = summary.totals.income,
                    currency = summary.baseCurrency,
                    type = TransactionType.INCOME,
                    modifier = Modifier.weight(1f),
                )
                DirectionalTotal(
                    label = stringResource(R.string.home_spent),
                    money = summary.totals.expense,
                    currency = summary.baseCurrency,
                    type = TransactionType.EXPENSE,
                    modifier = Modifier.weight(1f),
                )
            }

            ComparisonLine(summary = summary)

            if (summary.mixedCurrency) {
                Spacer(Modifier.height(8.dp))
                ConvertedNote()
            }
        }
    }
}

@Composable
private fun DirectionalTotal(
    label: String,
    money: Money,
    currency: Currency,
    type: TransactionType,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.expenseColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (type.isIncome) {
                    Icons.Filled.ArrowDownward
                } else {
                    Icons.Filled.ArrowUpward
                },
                contentDescription = null,
                tint = if (type.isIncome) colors.income else colors.expense,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AmountText(
            money = money,
            currency = currency,
            type = type,
            showSign = false,
            style = AmountTextStyles.Large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * How this month compares with the same stretch of last month.
 *
 * Deliberately like-for-like: eleven days of this month against eleven days of last
 * month. Comparing a part-month against a whole one would report a fall every time,
 * every month, which is worse than saying nothing.
 */
@Composable
private fun ComparisonLine(summary: DashboardSummary, modifier: Modifier = Modifier) {
    val percent = summary.expenseChangePercent
    val rounded = percent?.roundToInt()

    val text = when {
        rounded == null -> stringResource(R.string.home_compare_first)
        rounded == 0 -> stringResource(R.string.home_compare_same)
        rounded > 0 -> stringResource(
            R.string.home_compare_up,
            stringResource(R.string.home_percent, rounded),
        )
        else -> stringResource(
            R.string.home_compare_down,
            stringResource(R.string.home_percent, abs(rounded)),
        )
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ConvertedNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_converted_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// -- Today and daily average ------------------------------------------------

@Composable
private fun QuickStatsRow(summary: DashboardSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            label = stringResource(R.string.home_today),
            money = summary.todaySpent,
            currency = summary.baseCurrency,
            caption = if (summary.todaySpentMinor == 0L) {
                stringResource(R.string.home_nothing_today)
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.home_daily_average),
            money = summary.dailyAverage,
            currency = summary.baseCurrency,
            caption = pluralStringResource(
                R.plurals.home_days_in,
                summary.averageDays,
                summary.averageDays,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    money: Money,
    currency: Currency,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Card(modifier = modifier, colors = cardColors()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(min = 76.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AmountText(
                money = money,
                currency = currency,
                showSign = false,
                style = AmountTextStyles.Large,
                modifier = Modifier.fillMaxWidth(),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// -- Seven-day chart --------------------------------------------------------

@Composable
private fun WeekCard(summary: DashboardSummary, modifier: Modifier = Modifier) {
    val nothingSpent = summary.dailySpend.all { it.amountMinor == 0L }

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_week_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (nothingSpent) {
                Text(
                    text = stringResource(R.string.home_week_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DailySpendBars(
                    days = summary.dailySpend,
                    currency = summary.baseCurrency,
                )
            }
        }
    }
}

// -- Budgets ----------------------------------------------------------------

@Composable
private fun BudgetRow(summary: BudgetSummary, modifier: Modifier = Modifier) {
    val progress = summary.progress
    val colors = MaterialTheme.expenseColors
    val statusColor = when (progress.status) {
        BudgetStatus.NORMAL -> colors.budgetNormal
        BudgetStatus.WARNING -> colors.budgetWarning
        BudgetStatus.CRITICAL -> colors.budgetCritical
        BudgetStatus.EXCEEDED -> colors.budgetExceeded
    }
    val statusLabel = when (progress.status) {
        BudgetStatus.NORMAL -> stringResource(R.string.home_budget_status_normal)
        BudgetStatus.WARNING -> stringResource(R.string.home_budget_status_warning)
        BudgetStatus.CRITICAL -> stringResource(R.string.home_budget_status_critical)
        BudgetStatus.EXCEEDED -> stringResource(R.string.home_budget_status_exceeded)
    }

    val budgetCurrency = summary.currency
    val spentOf = stringResource(
        R.string.home_budget_spent_of,
        MoneyFormatter.format(progress.spent, budgetCurrency),
        MoneyFormatter.format(progress.budget.limit, budgetCurrency),
    )
    val remainder = if (progress.remainingMinor < 0) {
        stringResource(
            R.string.home_budget_over,
            MoneyFormatter.format(progress.remaining.abs(), budgetCurrency),
        )
    } else {
        stringResource(
            R.string.home_budget_remaining,
            MoneyFormatter.format(progress.remaining, budgetCurrency),
        )
    }

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                // One spoken sentence per budget. Status is carried by the words as
                // well as the colour, so it survives both TalkBack and colour blindness.
                .clearAndSetSemantics {
                    contentDescription = "${summary.label}. $statusLabel. $spentOf. $remainder."
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
            }

            ShareBar(fraction = progress.fraction, color = statusColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = spentOf,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = remainder,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.remainingMinor < 0) statusColor else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (progress.mixedCurrency) ConvertedNote()
        }
    }
}

// -- Top categories ---------------------------------------------------------

@Composable
private fun CategorySpendRow(
    spend: CategorySpend,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val color = Color(spend.colorArgb)
    val percent = (spend.share * 100).roundToInt()
    val formatted = MoneyFormatter.format(spend.amount, currency)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = "${spend.name}, $formatted, $percent percent of spending"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons[spend.iconKey],
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = spend.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.home_percent, percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ShareBar(fraction = spend.share, color = color)
        }

        AmountText(
            money = spend.amount,
            currency = currency,
            showSign = false,
            style = AmountTextStyles.Row,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// -- Quick actions ----------------------------------------------------------

@Composable
private fun QuickActions(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                icon = Icons.Filled.Remove,
                label = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.action_add_income),
                onClick = onAddIncome,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                label = stringResource(R.string.title_expenses),
                onClick = onSeeAllTransactions,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Filled.Category,
                label = stringResource(R.string.title_categories),
                onClick = onCategories,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// -- Shared bits ------------------------------------------------------------

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun cardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

private const val MAX_BUDGET_ROWS = 3

// -- Previews ---------------------------------------------------------------

@Preview(name = "Dashboard", showBackground = true, heightDp = 1400)
@Composable
private fun HomeScreenPreview() {
    PaisaTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, summary = previewSummary()),
            onEvent = {},
            onAddExpense = {},
            onAddIncome = {},
            onSeeAllTransactions = {},
            onTransactionClick = {},
            onCategories = {},
            onBudgets = {},
        )
    }
}

@Preview(name = "Dashboard · dark", showBackground = true, heightDp = 1400, uiMode = 32)
@Composable
private fun HomeScreenDarkPreview() {
    PaisaTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, summary = previewSummary()),
            onEvent = {},
            onAddExpense = {},
            onAddIncome = {},
            onSeeAllTransactions = {},
            onTransactionClick = {},
            onCategories = {},
            onBudgets = {},
        )
    }
}

@Preview(name = "Dashboard · empty", showBackground = true, heightDp = 700)
@Composable
private fun HomeScreenEmptyPreview() {
    PaisaTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                summary = previewSummary(empty = true),
            ),
            onEvent = {},
            onAddExpense = {},
            onAddIncome = {},
            onSeeAllTransactions = {},
            onTransactionClick = {},
            onCategories = {},
            onBudgets = {},
        )
    }
}

@Preview(name = "Dashboard · error", showBackground = true, heightDp = 700)
@Composable
private fun HomeScreenErrorPreview() {
    PaisaTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, error = "PKR has an unusable rate."),
            onEvent = {},
            onAddExpense = {},
            onAddIncome = {},
            onSeeAllTransactions = {},
            onTransactionClick = {},
            onCategories = {},
            onBudgets = {},
        )
    }
}

private fun previewSummary(empty: Boolean = false): DashboardSummary {
    val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    val today = LocalDate.of(2026, 9, 12)
    val month = YearMonth.from(today)

    val food = Category(
        id = "cat-food",
        name = "Food",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0xFFE07A5F.toInt(),
    )
    val transport = Category(
        id = "cat-transport",
        name = "Transport",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "car",
        colorArgb = 0xFF3F6BB5.toInt(),
    )

    if (empty) {
        return DashboardSummary(
            today = today,
            month = month,
            baseCurrency = pkr,
            totals = TransactionTotals.empty("PKR"),
            todaySpentMinor = 0,
            dailyAverageMinor = 0,
            averageDays = 10,
            previousMonthToDateExpenseMinor = 0,
            topCategories = emptyList(),
            budgets = emptyList(),
            recent = emptyList(),
            dailySpend = (0..6).map {
                DailySpend(today.minusDays((6 - it).toLong()), 0L, "PKR")
            },
            mixedCurrency = false,
            hasAnyTransactions = false,
        )
    }

    fun transaction(id: String, amount: Long, category: Category, description: String, day: Int) =
        Transaction(
            id = id,
            type = TransactionType.EXPENSE,
            amountMinor = amount,
            currencyCode = "PKR",
            categoryId = category.id,
            description = description,
            date = LocalDate.of(2026, 9, day),
            time = LocalTime.of(13, 5),
        )

    val recent = listOf(
        transaction("t1", 85000, food, "Burger", 12),
        transaction("t2", 30000, transport, "Rickshaw", 12),
        transaction("t3", 240000, food, "Groceries", 11),
    ).map { record ->
        TransactionDetails(
            transaction = record,
            category = if (record.categoryId == food.id) food else transport,
            subcategory = null,
            paymentMethod = null,
            currency = pkr,
        )
    }

    return DashboardSummary(
        today = today,
        month = month,
        baseCurrency = pkr,
        totals = TransactionTotals(
            income = Money(15_000_00, "PKR"),
            expense = Money(4_820_00, "PKR"),
            mixedCurrency = true,
            count = 24,
        ),
        todaySpentMinor = 1_150_00,
        dailyAverageMinor = 401_00,
        averageDays = 10,
        previousMonthToDateExpenseMinor = 4_100_00,
        topCategories = listOf(
            CategorySpend("cat-food", "Food", "restaurant", food.colorArgb, 2_400_00, "PKR", 0.50f),
            CategorySpend(
                "cat-transport", "Transport", "car", transport.colorArgb, 1_220_00, "PKR", 0.25f,
            ),
        ),
        budgets = listOf(
            BudgetSummary(
                progress = BudgetProgress(
                    budget = Budget(
                        id = "b1",
                        categoryId = "cat-food",
                        limitMinor = 3_000_00,
                        currencyCode = "PKR",
                    ),
                    month = month,
                    spentMinor = 2_400_00,
                    mixedCurrency = false,
                ),
                category = food,
                subcategory = null,
                currency = pkr,
            ),
        ),
        recent = recent,
        dailySpend = listOf(320_00L, 0L, 780_00L, 145_00L, 90_00L, 610_00L, 1_150_00L)
            .mapIndexed { index, amount ->
                DailySpend(today.minusDays((6 - index).toLong()), amount, "PKR")
            },
        mixedCurrency = true,
        hasAnyTransactions = true,
    )
}
