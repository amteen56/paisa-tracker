package com.amteen.paisa.ui.screen.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amteen.paisa.core.time.PeriodFilter
import com.amteen.paisa.domain.model.TransactionSort
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.ui.components.AmountText
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.NetAmountText
import com.amteen.paisa.ui.components.TransactionRow
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.expenseColors

/**
 * Transaction history: search, filters, four sort modes, sticky date headers.
 *
 * The list is a `LazyColumn` keyed by transaction id — with thousands of records
 * nothing may render eagerly, and a stable key is what keeps scroll position and
 * animations correct when the query changes. See CLAUDE.md.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionHistoryScreen(
    state: TransactionHistoryUiState,
    onEvent: (TransactionHistoryEvent) -> Unit,
    onTransactionClick: (String) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Transactions") },
                    actions = {
                        IconButton(onClick = { onEvent(TransactionHistoryEvent.ToggleSearch) }) {
                            Icon(
                                imageVector = if (state.searchVisible) {
                                    Icons.Filled.Close
                                } else {
                                    Icons.Filled.Search
                                },
                                contentDescription = if (state.searchVisible) {
                                    "Close search"
                                } else {
                                    "Search transactions"
                                },
                            )
                        }

                        androidx.compose.foundation.layout.Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Change sort order",
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false },
                            ) {
                                TransactionSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.label) },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = state.query.sort == sort,
                                                onClick = null,
                                            )
                                        },
                                        onClick = {
                                            onEvent(TransactionHistoryEvent.SortSelected(sort))
                                            sortMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        BadgedBox(
                            badge = {
                                if (state.query.activeFilterCount > 0) {
                                    Badge { Text(state.query.activeFilterCount.toString()) }
                                }
                            },
                        ) {
                            IconButton(onClick = { onEvent(TransactionHistoryEvent.OpenFilters) }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter transactions")
                            }
                        }
                    },
                )

                AnimatedVisibility(visible = state.searchVisible) {
                    OutlinedTextField(
                        value = state.query.text,
                        onValueChange = { onEvent(TransactionHistoryEvent.SearchChanged(it)) },
                        placeholder = { Text("Search descriptions, categories, notes") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                PeriodChips(
                    selected = state.query.period,
                    onSelect = { onEvent(TransactionHistoryEvent.PeriodSelected(it)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading -> LoadingState()

                state.isEmpty -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = if (state.query.hasQuery) {
                        "Nothing matches those filters"
                    } else {
                        "No transactions yet"
                    },
                    message = if (state.query.hasQuery) {
                        "Try a wider period, or clear the filters to see everything."
                    } else {
                        "Add your first expense and it will show up here, grouped by day."
                    },
                    actionLabel = if (state.query.hasQuery) "Clear filters" else "Add a transaction",
                    onAction = if (state.query.hasQuery) {
                        { onEvent(TransactionHistoryEvent.ClearFilters) }
                    } else {
                        onAddTransaction
                    },
                )

                else -> {
                    SummaryCard(state)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        state.sections.forEach { section ->
                            if (section.date != null) {
                                stickyHeader(key = "header-${section.date}") {
                                    SectionHeader(section.label, section)
                                }
                            }
                            items(
                                items = section.items,
                                // A stable key, not the index — otherwise editing
                                // one row re-binds every row below it.
                                key = { it.id },
                            ) { details ->
                                TransactionRow(
                                    details = details,
                                    onClick = { onTransactionClick(details.id) },
                                    showTime = state.query.sort == TransactionSort.NEWEST_FIRST ||
                                        state.query.sort == TransactionSort.OLDEST_FIRST,
                                )
                            }
                        }

                        // The bottom bar and FAB float over the list; without this
                        // the last row is unreachable behind them.
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
    }

    if (state.filterSheetVisible) {
        FilterSheet(
            state = state,
            onEvent = onEvent,
            onDismiss = { onEvent(TransactionHistoryEvent.DismissFilters) },
        )
    }
}

@Composable
private fun PeriodChips(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit) {
    val options = remember {
        listOf(
            "This month" to PeriodFilter.ThisMonth,
            "Last month" to PeriodFilter.LastMonth,
            "This week" to PeriodFilter.ThisWeek,
            "Today" to PeriodFilter.Today,
            "This year" to PeriodFilter.ThisYear,
            "All time" to PeriodFilter.AllTime,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, period) ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SummaryCard(state: TransactionHistoryUiState) {
    val colors = MaterialTheme.expenseColors

    // The amount styles right-align, which is what a list column wants but not a
    // labelled figure — here the number sits directly under its own label.
    val net = AmountTextStyles.Large.copy(textAlign = TextAlign.Start)
    val figure = AmountTextStyles.Row.copy(textAlign = TextAlign.Start)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Net gets the full width and the largest type: it is the figure the
            // user actually came for. Splitting three amounts across equal thirds
            // left no room for a six-figure salary, which then rendered as "+Rs.".
            SummaryFigure(label = "Net", modifier = Modifier.fillMaxWidth()) {
                NetAmountText(
                    money = state.totals.net,
                    currency = state.baseCurrency,
                    style = net,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryFigure(
                    label = "Income",
                    modifier = Modifier.weight(1f),
                ) {
                    AmountText(
                        money = state.totals.income,
                        currency = state.baseCurrency,
                        type = TransactionType.INCOME,
                        style = figure,
                        showSign = false,
                        color = colors.income,
                    )
                }
                SummaryFigure(
                    label = "Expense",
                    modifier = Modifier.weight(1f),
                ) {
                    AmountText(
                        money = state.totals.expense,
                        currency = state.baseCurrency,
                        type = TransactionType.EXPENSE,
                        style = figure,
                        showSign = false,
                        color = colors.expense,
                    )
                }
            }

        }
    }
}

@Composable
private fun SummaryFigure(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        value()
    }
}

@Composable
private fun SectionHeader(
    label: String,
    section: com.amteen.paisa.domain.usecase.TransactionSection,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${section.items.size} " + if (section.items.size == 1) "entry" else "entries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: TransactionHistoryUiState,
    onEvent: (TransactionHistoryEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (state.query.activeFilterCount > 0) {
                    TextButton(onClick = { onEvent(TransactionHistoryEvent.ClearFilters) }) {
                        Text("Clear all")
                    }
                }
            }

            FilterGroup("Type") {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = type in state.query.types,
                        onClick = { onEvent(TransactionHistoryEvent.TypeToggled(type)) },
                        label = { Text(if (type.isIncome) "Income" else "Expense") },
                    )
                }
            }

            FilterGroup("Category") {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = category.id in state.query.categoryIds,
                        onClick = { onEvent(TransactionHistoryEvent.CategoryToggled(category.id)) },
                        label = { Text(category.name) },
                    )
                }
            }

            if (state.paymentMethods.isNotEmpty()) {
                FilterGroup("Paid with") {
                    state.paymentMethods.forEach { method ->
                        FilterChip(
                            selected = method.id in state.query.paymentMethodIds,
                            onClick = {
                                onEvent(TransactionHistoryEvent.PaymentMethodToggled(method.id))
                            },
                            label = { Text(method.name) },
                        )
                    }
                }
            }

        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
