package com.amteen.paisa.ui.screen.budget

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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
import com.amteen.paisa.domain.usecase.BudgetSummary
import com.amteen.paisa.ui.charts.ShareBar
import com.amteen.paisa.ui.components.ConfirmDialog
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * The budgets screen.
 *
 * Renders [BudgetListUiState] and nothing else — every figure comes from
 * `GetBudgetStatusUseCase`, the same one the dashboard strip uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetListScreen(
    state: BudgetListUiState,
    onEvent: (BudgetListEvent) -> Unit,
    onAddBudget: () -> Unit,
    onEditBudget: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(BudgetListEvent.MessageShown)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(BudgetListEvent.DismissError)
        }
    }

    NotificationPermissionEffect(
        enabled = state.shouldRequestNotificationPermission,
        onRequested = { onEvent(BudgetListEvent.NotificationPermissionRequested) },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_budgets)) },
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
        floatingActionButton = {
            if (!state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onAddBudget,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.budget_add)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            state.isLoading -> LoadingState(modifier = content)

            state.isEmpty -> Column(modifier = content) {
                MonthBar(state = state, onEvent = onEvent)
                EmptyState(
                    icon = Icons.Filled.Savings,
                    title = stringResource(R.string.budget_empty_title),
                    message = stringResource(R.string.budget_empty_message),
                    actionLabel = stringResource(R.string.budget_empty_action),
                    onAction = onAddBudget,
                )
            }

            else -> Column(modifier = content) {
                MonthBar(state = state, onEvent = onEvent)
                BudgetList(
                    state = state,
                    onEvent = onEvent,
                    onEditBudget = onEditBudget,
                )
            }
        }
    }

    state.pendingRemoval?.let { pending ->
        ConfirmDialog(
            title = stringResource(R.string.budget_delete_title, pending.label),
            message = stringResource(R.string.budget_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(BudgetListEvent.RemoveConfirmed) },
            onDismiss = { onEvent(BudgetListEvent.RemoveDismissed) },
            destructive = true,
        )
    }
}

/**
 * Asks for notification permission, once, and only when there is a budget that could
 * actually produce an alert.
 *
 * On Android 12 and below there is no runtime permission and this does nothing.
 */
@Composable
private fun NotificationPermissionEffect(enabled: Boolean, onRequested: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        // Nothing to do with the answer: if it is denied, BudgetAlertNotifier simply
        // does not post, and the alert stays pending rather than being consumed.
        onResult = {},
    )

    LaunchedEffect(enabled) {
        if (enabled && !asked) {
            asked = true
            onRequested()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun MonthBar(
    state: BudgetListUiState,
    onEvent: (BudgetListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(BudgetListEvent.PreviousMonth) }) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.budget_previous_month),
            )
        }
        Text(
            text = DateFormatters.month(state.month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onEvent(BudgetListEvent.NextMonth) }) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.budget_next_month),
            )
        }
    }

    if (state.month != YearMonth.now()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { onEvent(BudgetListEvent.ThisMonth) }) {
                Text(stringResource(R.string.budget_this_month))
            }
        }
    }
}

@Composable
private fun BudgetList(
    state: BudgetListUiState,
    onEvent: (BudgetListEvent) -> Unit,
    onEditBudget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.totalLimit != null && state.totalSpent != null) {
            item(key = "totals") {
                TotalsCard(
                    limit = state.totalLimit,
                    spent = state.totalSpent,
                    currency = state.baseCurrency,
                )
            }
        }

        items(state.summaries, key = { "budget-${it.id}" }) { summary ->
            BudgetCard(
                summary = summary,
                onEdit = { onEditBudget(summary.id) },
                onArchive = { onEvent(BudgetListEvent.ArchiveToggled(summary.id, true)) },
                onDelete = { onEvent(BudgetListEvent.RemoveRequested(summary.id)) },
            )
        }

        item(key = "alerts") {
            AlertsToggle(
                enabled = state.alertsEnabled,
                onToggle = { onEvent(BudgetListEvent.AlertsToggled(it)) },
            )
        }

        if (state.archived.isNotEmpty()) {
            item(key = "archived-header") {
                TextButton(onClick = { onEvent(BudgetListEvent.ToggleArchivedVisible) }) {
                    Icon(
                        imageVector = if (state.archivedVisible) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(
                            R.string.budget_archived_count,
                            state.archived.size,
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (state.archivedVisible) {
                items(state.archived, key = { "archived-${it.id}" }) { summary ->
                    BudgetCard(
                        summary = summary,
                        archived = true,
                        onEdit = { onEditBudget(summary.id) },
                        onArchive = { onEvent(BudgetListEvent.ArchiveToggled(summary.id, false)) },
                        onDelete = { onEvent(BudgetListEvent.RemoveRequested(summary.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(
    limit: Money,
    spent: Money,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.expenseColors
    val fraction = if (limit.amountMinor <= 0L) 0f
    else (spent.amountMinor.toDouble() / limit.amountMinor.toDouble())
        .coerceIn(0.0, 1.0).toFloat()

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_totals_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.budget_totals_body,
                    MoneyFormatter.format(spent, currency),
                    MoneyFormatter.format(limit, currency),
                ),
                style = AmountTextStyles.Large,
            )
            ShareBar(
                fraction = fraction,
                color = if (spent > limit) colors.budgetExceeded else colors.budgetNormal,
            )
        }
    }
}

@Composable
private fun BudgetCard(
    summary: BudgetSummary,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    archived: Boolean = false,
) {
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

    val spentOf = stringResource(
        R.string.home_budget_spent_of,
        MoneyFormatter.format(progress.spent, summary.currency),
        MoneyFormatter.format(progress.budget.limit, summary.currency),
    )
    val remainder = if (progress.remainingMinor < 0) {
        stringResource(
            R.string.home_budget_over,
            MoneyFormatter.format(progress.remaining.abs(), summary.currency),
        )
    } else {
        stringResource(
            R.string.home_budget_remaining,
            MoneyFormatter.format(progress.remaining, summary.currency),
        )
    }

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // The card body speaks as one sentence; the menu button keeps
                        // its own label so it stays reachable.
                        .clearAndSetSemantics {
                            contentDescription =
                                "${summary.label}. $statusLabel. $spentOf. $remainder."
                        },
                ) {
                    Text(
                        text = summary.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (progress.budget.isRecurring) {
                            stringResource(R.string.budget_recurring_badge)
                        } else {
                            DateFormatters.month(progress.month)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (archived) MaterialTheme.colorScheme.onSurfaceVariant else statusColor,
                )
                BudgetMenu(
                    label = summary.label,
                    archived = archived,
                    onEdit = onEdit,
                    onArchive = onArchive,
                    onDelete = onDelete,
                )
            }

            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShareBar(
                    fraction = progress.fraction,
                    color = if (archived) MaterialTheme.colorScheme.outline else statusColor,
                )
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
                        text = "${progress.percent.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = remainder,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.remainingMinor < 0 && !archived) {
                        statusColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun BudgetMenu(
    label: String,
    archived: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.budget_actions_for, label),
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                expanded = false
                onEdit()
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (archived) R.string.action_restore else R.string.action_archive,
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    contentDescription = null,
                )
            },
            onClick = {
                expanded = false
                onArchive()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

@Composable
private fun AlertsToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.budget_alerts_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                text = stringResource(R.string.budget_alerts_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun cardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

// -- Previews ---------------------------------------------------------------

@Preview(name = "Budgets", showBackground = true, heightDp = 900)
@Composable
private fun BudgetListPreview() {
    PaisaTheme {
        BudgetListScreen(
            state = previewState(),
            onEvent = {},
            onAddBudget = {},
            onEditBudget = {},
            onBack = {},
        )
    }
}

@Preview(name = "Budgets · dark", showBackground = true, heightDp = 900, uiMode = 32)
@Composable
private fun BudgetListDarkPreview() {
    PaisaTheme {
        BudgetListScreen(
            state = previewState(),
            onEvent = {},
            onAddBudget = {},
            onEditBudget = {},
            onBack = {},
        )
    }
}

@Preview(name = "Budgets · empty", showBackground = true, heightDp = 700)
@Composable
private fun BudgetListEmptyPreview() {
    PaisaTheme {
        BudgetListScreen(
            state = BudgetListUiState(isLoading = false),
            onEvent = {},
            onAddBudget = {},
            onEditBudget = {},
            onBack = {},
        )
    }
}

private fun previewState(): BudgetListUiState {
    val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    val month = YearMonth.of(2026, 9)

    fun summary(
        id: String,
        name: String,
        limit: Long,
        spent: Long,
        recurring: Boolean = true,
    ): BudgetSummary {
        val category = Category(
            id = "cat-$id",
            name = name,
            applicableTo = CategoryScope.EXPENSE,
            iconKey = "restaurant",
            colorArgb = 0xFFE07A5F.toInt(),
        )
        return BudgetSummary(
            progress = BudgetProgress(
                budget = Budget(
                    id = id,
                    categoryId = category.id,
                    limitMinor = limit,
                    currencyCode = "PKR",
                    period = if (recurring) null else month,
                ),
                month = month,
                spentMinor = spent,
            ),
            category = category,
            subcategory = null,
            currency = pkr,
        )
    }

    return BudgetListUiState(
        isLoading = false,
        month = month,
        summaries = listOf(
            summary("b1", "Food", 3_000_00, 2_880_00),
            summary("b2", "Transport", 2_000_00, 2_400_00),
            summary("b3", "Shopping", 5_000_00, 1_100_00, recurring = false),
        ),
        totalLimit = Money(10_000_00, "PKR"),
        totalSpent = Money(6_380_00, "PKR"),
        baseCurrency = pkr,
    )
}
