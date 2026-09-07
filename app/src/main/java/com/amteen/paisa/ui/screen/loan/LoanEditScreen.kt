package com.amteen.paisa.ui.screen.loan

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Repayment
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.theme.PaisaTheme
import java.time.LocalDate

/**
 * The loan form, backing both Add and Edit.
 *
 * On an existing loan the repayment history sits below the fields, read-only apart from
 * a per-row remove. Recording a repayment belongs on the list, next to the outstanding
 * figure; what this screen is for is fixing one that was entered wrong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanEditScreen(
    state: LoanEditUiState,
    onEvent: (LoanEditEvent) -> Unit,
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
            onEvent(LoanEditEvent.DismissError)
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
                                R.string.loan_edit_title
                            } else {
                                R.string.loan_new_title
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
                        onClick = { onEvent(LoanEditEvent.Save) },
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "direction") {
                DirectionPicker(
                    direction = state.direction,
                    onSelect = { onEvent(LoanEditEvent.DirectionChanged(it)) },
                )
            }

            item(key = "fields") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.counterpartyInput,
                        onValueChange = { onEvent(LoanEditEvent.CounterpartyChanged(it)) },
                        label = { Text(stringResource(R.string.loan_counterparty_label)) },
                        placeholder = {
                            Text(stringResource(R.string.loan_counterparty_placeholder))
                        },
                        isError = state.counterpartyError != null,
                        supportingText = state.counterpartyError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = { onEvent(LoanEditEvent.AmountChanged(it)) },
                        label = { Text(stringResource(R.string.loan_amount_label)) },
                        prefix = { Text(state.currency.symbol) },
                        isError = state.amountError != null,
                        supportingText = state.amountError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.noteInput,
                        onValueChange = { onEvent(LoanEditEvent.NoteChanged(it)) },
                        label = { Text(stringResource(R.string.loan_note_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "dates") {
                DateRow(state = state, onEvent = onEvent)
            }

            if (state.isEditing) {
                item(key = "repayments-header") {
                    Text(
                        text = stringResource(R.string.loan_repayment_history),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item(key = "repayments") {
                    RepaymentHistory(
                        rows = state.repayments,
                        currency = state.currency,
                        onDelete = { onEvent(LoanEditEvent.RepaymentDeleted(it)) },
                    )
                }
            }
        }
    }

    if (state.pickingDate != null) {
        LoanDatePickerDialog(
            initial = state.pickerInitialDate,
            onSelect = { onEvent(LoanEditEvent.DatePicked(it)) },
            onDismiss = { onEvent(LoanEditEvent.DismissDatePicker) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionPicker(
    direction: LoanDirection,
    onSelect: (LoanDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.loan_direction_label),
            style = MaterialTheme.typography.bodyMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LoanDirection.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == direction,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = LoanDirection.entries.size,
                    ),
                ) {
                    Text(
                        when (option) {
                            LoanDirection.LENT -> stringResource(R.string.loan_direction_lent)
                            LoanDirection.BORROWED ->
                                stringResource(R.string.loan_direction_borrowed)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateRow(
    state: LoanEditUiState,
    onEvent: (LoanEditEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { onEvent(LoanEditEvent.OpenDatePicker(LoanEditUiState.DateField.LOANED)) },
                label = { Text(DateFormatters.fullDate(state.date)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clearAndSetSemantics {
                        contentDescription = "Date, ${DateFormatters.fullDate(state.date)}"
                    },
            )

            AssistChip(
                onClick = { onEvent(LoanEditEvent.OpenDatePicker(LoanEditUiState.DateField.DUE)) },
                label = {
                    Text(
                        state.dueDate
                            ?.let { stringResource(R.string.loan_due_on, DateFormatters.fullDate(it)) }
                            ?: stringResource(R.string.loan_due_date_none),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (state.dueDate == null) {
                    null
                } else {
                    {
                        IconButton(onClick = { onEvent(LoanEditEvent.DueDateCleared) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription =
                                    stringResource(R.string.loan_due_date_clear),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }

        state.dueDateError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RepaymentHistory(
    rows: List<RepaymentRowState>,
    currency: Currency,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.loan_repayment_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Card
        }

        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            rows.forEach { row ->
                RepaymentRow(row = row, currency = currency, onDelete = { onDelete(row.id) })
            }
        }
    }
}

@Composable
private fun RepaymentRow(
    row: RepaymentRowState,
    currency: Currency,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = MoneyFormatter.format(row.repayment.amount, currency)
    val date = DateFormatters.fullDate(row.repayment.date)
    val method = row.paymentMethod?.name
        ?: stringResource(R.string.loan_repayment_method_none)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = "$amount, $date, $method" },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = amount, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "$date · $method",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.loan_repayment_delete),
            )
        }
    }
}

// -- Previews ---------------------------------------------------------------

private val previewEditCurrency = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)

private fun previewEditState(editing: Boolean = true) = LoanEditUiState(
    isLoading = false,
    isEditing = editing,
    counterpartyInput = if (editing) "Ali" else "",
    direction = LoanDirection.LENT,
    amountInput = if (editing) "5000.00" else "",
    currency = previewEditCurrency,
    date = LocalDate.of(2026, 8, 20),
    dueDate = if (editing) LocalDate.of(2026, 9, 20) else null,
    noteInput = if (editing) "For the bike repair" else "",
    repayments = if (editing) {
        listOf(
            RepaymentRowState(
                Repayment("r1", 200_000, LocalDate.of(2026, 9, 1), "pm-cash"),
                PaymentMethod("pm-cash", "Cash", "cash"),
            ),
            RepaymentRowState(
                Repayment("r2", 50_000, LocalDate.of(2026, 8, 28), null),
                null,
            ),
        )
    } else {
        emptyList()
    },
)

@Preview(name = "Loan · edit", showBackground = true, heightDp = 900)
@Composable
private fun LoanEditScreenPreview() {
    PaisaTheme {
        LoanEditScreen(state = previewEditState(), onEvent = {}, onBack = {})
    }
}

@Preview(name = "Loan · edit dark", showBackground = true, heightDp = 900, uiMode = 32)
@Composable
private fun LoanEditScreenDarkPreview() {
    PaisaTheme {
        LoanEditScreen(state = previewEditState(), onEvent = {}, onBack = {})
    }
}

@Preview(name = "Loan · new", showBackground = true, heightDp = 700)
@Composable
private fun LoanEditScreenNewPreview() {
    PaisaTheme {
        LoanEditScreen(state = previewEditState(editing = false), onEvent = {}, onBack = {})
    }
}
