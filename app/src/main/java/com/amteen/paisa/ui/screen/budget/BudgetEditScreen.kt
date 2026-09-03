package com.amteen.paisa.ui.screen.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetProgress
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.ui.charts.ShareBar
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * The budget form, backing both Add and Edit.
 *
 * On an existing budget the recent months are shown below the fields. Setting a
 * sensible limit is much easier next to what the last six months actually cost, and
 * the figures are already derived — this only renders them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditScreen(
    state: BudgetEditUiState,
    onEvent: (BudgetEditEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(BudgetEditEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) {
                                R.string.budget_edit_title
                            } else {
                                R.string.budget_new_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onEvent(BudgetEditEvent.Save) },
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            state.isLoading -> LoadingState(modifier = content)

            state.categories.isEmpty() -> EmptyState(
                icon = Icons.Filled.Category,
                title = stringResource(R.string.budget_no_categories_title),
                message = stringResource(R.string.budget_no_categories_message),
                modifier = content,
            )

            else -> BudgetForm(state = state, onEvent = onEvent, modifier = content)
        }
    }
}

@Composable
private fun BudgetForm(
    state: BudgetEditUiState,
    onEvent: (BudgetEditEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "limit") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.limitInput,
                    onValueChange = { onEvent(BudgetEditEvent.LimitChanged(it)) },
                    label = { Text(stringResource(R.string.budget_limit_label)) },
                    prefix = { Text(state.currency.symbol) },
                    singleLine = true,
                    isError = state.limitError != null,
                    supportingText = state.limitError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.currencies.size > 1) {
                    CurrencyChips(state = state, onEvent = onEvent)
                }
            }
        }

        item(key = "category") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(stringResource(R.string.budget_category_label))
                CategoryChips(state = state, onEvent = onEvent)
                state.categoryError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.subcategories.isNotEmpty()) {
            item(key = "subcategory") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(stringResource(R.string.budget_subcategory_label))
                    SubcategoryChips(
                        subcategories = state.subcategories,
                        selectedId = state.selectedSubcategoryId,
                        onSelect = { onEvent(BudgetEditEvent.SubcategorySelected(it)) },
                    )
                }
            }
        }

        item(key = "period") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(stringResource(R.string.budget_period_label))
                PeriodToggle(state = state, onEvent = onEvent)
                if (state.pinnedToMonth) {
                    MonthStepper(state = state, onEvent = onEvent)
                }
            }
        }

        if (state.isEditing) {
            item(key = "history-header") {
                Label(stringResource(R.string.budget_history_title))
            }
            item(key = "history") {
                HistoryCard(state = state)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChips(state: BudgetEditUiState, onEvent: (BudgetEditEvent) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.currencies.forEach { currency ->
            FilterChip(
                selected = currency.code == state.currency.code,
                onClick = { onEvent(BudgetEditEvent.CurrencySelected(currency.code)) },
                label = { Text(currency.code) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(state: BudgetEditUiState, onEvent: (BudgetEditEvent) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.categories.forEach { category ->
            FilterChip(
                selected = category.id == state.selectedCategoryId,
                onClick = { onEvent(BudgetEditEvent.CategorySelected(category.id)) },
                label = { Text(category.name) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubcategoryChips(
    subcategories: List<Subcategory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        subcategories.forEach { subcategory ->
            FilterChip(
                selected = subcategory.id == selectedId,
                onClick = { onSelect(subcategory.id) },
                label = { Text(subcategory.name) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodToggle(state: BudgetEditUiState, onEvent: (BudgetEditEvent) -> Unit) {
    val options = listOf(true, false)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, recurring ->
            SegmentedButton(
                selected = state.pinnedToMonth != recurring,
                onClick = { onEvent(BudgetEditEvent.RecurringChanged(recurring)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    stringResource(
                        if (recurring) {
                            R.string.budget_period_recurring
                        } else {
                            R.string.budget_period_single
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun MonthStepper(state: BudgetEditUiState, onEvent: (BudgetEditEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(BudgetEditEvent.PreviousMonth) }) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.budget_previous_month),
            )
        }
        Text(
            text = DateFormatters.month(state.period),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onEvent(BudgetEditEvent.NextMonth) }) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.budget_next_month),
            )
        }
    }
}

@Composable
private fun HistoryCard(state: BudgetEditUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.historyLoading -> Text(
                    text = stringResource(R.string.budget_history_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // `all` on an empty list is true, which is the right answer here:
                // a budget with no months yet has nothing to show either.
                state.history.all { it.spentMinor == 0L } -> Text(
                    text = stringResource(R.string.budget_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.history.forEach { progress ->
                    HistoryRow(progress = progress, currency = state.currency)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    progress: BudgetProgress,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.expenseColors
    val spent = MoneyFormatter.format(progress.spent, currency)
    val percent = progress.percent.roundToInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DateFormatters.month(progress.month),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "$spent · $percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ShareBar(
            fraction = progress.fraction,
            color = if (progress.remainingMinor < 0) colors.budgetExceeded else colors.budgetNormal,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// -- Previews ---------------------------------------------------------------

@Preview(name = "Budget editor", showBackground = true, heightDp = 900)
@Composable
private fun BudgetEditPreview() {
    PaisaTheme {
        BudgetEditScreen(state = previewEditState(), onEvent = {}, onBack = {})
    }
}

@Preview(name = "Budget editor · dark", showBackground = true, heightDp = 900, uiMode = 32)
@Composable
private fun BudgetEditDarkPreview() {
    PaisaTheme {
        BudgetEditScreen(state = previewEditState(), onEvent = {}, onBack = {})
    }
}

private fun previewEditState(): BudgetEditUiState {
    val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    val month = YearMonth.of(2026, 9)
    val food = Category(
        id = "cat-food",
        name = "Food",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0xFFE07A5F.toInt(),
        subcategories = listOf(Subcategory("sub-fast", "Fast Food")),
    )
    val budget = Budget(
        id = "b1",
        categoryId = food.id,
        limitMinor = 3_000_00,
        currencyCode = "PKR",
    )

    return BudgetEditUiState(
        isLoading = false,
        isEditing = true,
        limitInput = "3000.00",
        currency = pkr,
        currencies = listOf(pkr),
        categories = listOf(
            food,
            Category("cat-transport", "Transport", CategoryScope.EXPENSE, "car", 0xFF3F6BB5.toInt()),
        ),
        selectedCategoryId = food.id,
        period = month,
        history = listOf(2_880_00L, 3_400_00L, 2_100_00L).mapIndexed { index, spent ->
            BudgetProgress(budget, month.minusMonths(index.toLong()), spent)
        },
    )
}
