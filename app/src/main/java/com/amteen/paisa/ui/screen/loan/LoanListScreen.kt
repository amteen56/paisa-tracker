package com.amteen.paisa.ui.screen.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Repayment
import com.amteen.paisa.domain.usecase.LoanSlice
import com.amteen.paisa.domain.usecase.LoanSummary
import com.amteen.paisa.ui.charts.ShareBar
import com.amteen.paisa.ui.components.ConfirmDialog
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Money lent and borrowed.
 *
 * A standalone ledger, and the screen says so: nothing here counts as spending, so the
 * figures never line up with the dashboard's totals and are not meant to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanListScreen(
    state: LoanListUiState,
    onEvent: (LoanListEvent) -> Unit,
    onAddLoan: () -> Unit,
    onEditLoan: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(LoanListEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_loans)) },
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
            ExtendedFloatingActionButton(
                onClick = onAddLoan,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.loan_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            state.isLoading -> LoadingState(modifier = content)

            state.hasNoLoansAtAll -> EmptyState(
                icon = Icons.Outlined.Savings,
                title = stringResource(R.string.loan_empty_title),
                message = stringResource(R.string.loan_empty_message),
                actionLabel = stringResource(R.string.loan_empty_action),
                onAction = onAddLoan,
                modifier = content,
            )

            else -> LoanContent(
                state = state,
                onEvent = onEvent,
                onEditLoan = onEditLoan,
                modifier = content,
            )
        }
    }

    state.repaymentFor?.let { loan ->
        RepaymentSheet(loan = loan, state = state, onEvent = onEvent)
    }

    state.pendingDelete?.let { loan ->
        ConfirmDialog(
            title = stringResource(R.string.loan_delete_title),
            message = stringResource(R.string.loan_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(LoanListEvent.DeleteConfirmed) },
            onDismiss = { onEvent(LoanListEvent.DeleteDismissed) },
        )
    }
}

@Composable
private fun LoanContent(
    state: LoanListUiState,
    onEvent: (LoanListEvent) -> Unit,
    onEditLoan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.summary?.let { summary ->
            item(key = "summary") {
                BalanceCard(summary = summary, currency = state.currency)
            }
        }

        item(key = "filters") {
            FilterRow(selected = state.filter, onSelect = { onEvent(LoanListEvent.FilterChanged(it)) })
        }

        if (state.rows.isEmpty()) {
            item(key = "empty") {
                FilterEmptyMessage(filter = state.filter)
            }
        }

        items(items = state.rows, key = { it.id }) { row ->
            LoanCard(
                row = row,
                currency = state.currency,
                onRecordRepayment = { onEvent(LoanListEvent.RepaymentStarted(row.id)) },
                onEdit = { onEditLoan(row.id) },
                onDelete = { onEvent(LoanListEvent.DeleteRequested(row.id)) },
            )
        }
    }
}

// -- Header -----------------------------------------------------------------

@Composable
private fun BalanceCard(
    summary: LoanSummary,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.expenseColors

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BalanceColumn(
                    label = stringResource(R.string.loan_owed_to_you),
                    amount = MoneyFormatter.format(summary.outstandingLent, currency),
                    color = colors.income,
                    modifier = Modifier.weight(1f),
                )
                BalanceColumn(
                    label = stringResource(R.string.loan_you_owe),
                    amount = MoneyFormatter.format(summary.outstandingBorrowed, currency),
                    color = colors.expense,
                    modifier = Modifier.weight(1f),
                )
            }

            if (summary.overdueCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.home_loans_overdue,
                        summary.overdueCount,
                        summary.overdueCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.budgetExceeded,
                )
            }
        }
    }
}

@Composable
private fun BalanceColumn(
    label: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label, $amount" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = amount, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    selected: LoanFilter,
    onSelect: (LoanFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoanFilter.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        when (option) {
                            LoanFilter.OUTSTANDING ->
                                stringResource(R.string.loan_filter_outstanding)
                            LoanFilter.SETTLED -> stringResource(R.string.loan_filter_settled)
                            LoanFilter.ALL -> stringResource(R.string.loan_filter_all)
                        },
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/**
 * The filter found nothing, but the ledger is not empty.
 *
 * A different message to the screen-level empty state on purpose: "you have no loans"
 * shown to someone with ten settled ones reads as data loss.
 */
@Composable
private fun FilterEmptyMessage(filter: LoanFilter, modifier: Modifier = Modifier) {
    val (title, message) = when (filter) {
        LoanFilter.SETTLED -> stringResource(R.string.loan_empty_settled_title) to
            stringResource(R.string.loan_empty_settled_message)
        else -> stringResource(R.string.loan_empty_outstanding_title) to
            stringResource(R.string.loan_empty_outstanding_message)
    }

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -- One loan ---------------------------------------------------------------

@Composable
private fun LoanCard(
    row: LoanRowState,
    currency: Currency,
    onRecordRepayment: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loan = row.loan
    val colors = MaterialTheme.expenseColors
    val directionColor = if (loan.direction.isLent) colors.income else colors.expense

    val directionLine = stringResource(
        if (loan.direction.isLent) R.string.loan_lent_on else R.string.loan_borrowed_on,
        DateFormatters.compactDate(loan.date),
    )
    val progress = stringResource(
        R.string.loan_repaid_of,
        MoneyFormatter.format(loan.repaid, currency),
        MoneyFormatter.format(loan.principal, currency),
    )
    val statusLabel = when {
        loan.isSettled -> stringResource(R.string.loan_settled)
        row.isOverdue -> stringResource(R.string.loan_overdue)
        else -> null
    }
    val outstandingLine = if (loan.isSettled) {
        null
    } else {
        stringResource(
            R.string.loan_outstanding_amount,
            MoneyFormatter.format(loan.outstanding, currency),
        )
    }

    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // One spoken sentence per loan. Status is carried by words as
                        // well as colour, so it survives TalkBack and colour blindness.
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(
                                loan.counterparty,
                                statusLabel,
                                directionLine,
                                outstandingLine,
                                progress,
                            ).joinToString(". ") + "."
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = loan.counterparty,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = directionLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.isOverdue) colors.budgetExceeded else directionColor,
                    )
                }

                LoanRowMenu(
                    counterparty = loan.counterparty,
                    canRepay = !loan.isSettled,
                    onRecordRepayment = onRecordRepayment,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }

            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShareBar(fraction = loan.repaidFraction, color = directionColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (outstandingLine != null) {
                        Text(
                            text = outstandingLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = directionColor,
                        )
                    }
                }

                loan.dueDate?.takeIf { !loan.isSettled }?.let { due ->
                    Text(
                        text = stringResource(
                            R.string.loan_due_on,
                            DateFormatters.compactDate(due),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.isOverdue) {
                            colors.budgetExceeded
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (!loan.isSettled) {
                AssistChip(
                    onClick = onRecordRepayment,
                    label = { Text(stringResource(R.string.loan_record_repayment)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun LoanRowMenu(
    counterparty: String,
    canRepay: Boolean,
    onRecordRepayment: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.category_actions_for, counterparty),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canRepay) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.loan_record_repayment)) },
                    leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRecordRepayment()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
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
}

// -- Repayment sheet --------------------------------------------------------

/**
 * Recording money coming back.
 *
 * The amount arrives pre-filled with what is outstanding, because "they paid it all
 * back" is the common case and should not require retyping a figure already on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepaymentSheet(
    loan: Loan,
    state: LoanListUiState,
    onEvent: (LoanListEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onEvent(LoanListEvent.RepaymentDismissed) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.loan_repayment_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${loan.counterparty} · " + stringResource(
                        R.string.loan_outstanding_amount,
                        MoneyFormatter.format(loan.outstanding, state.currency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.repaymentAmountInput,
                onValueChange = { onEvent(LoanListEvent.RepaymentAmountChanged(it)) },
                label = { Text(stringResource(R.string.loan_repayment_amount_label)) },
                prefix = { Text(state.currency.symbol) },
                isError = state.repaymentAmountError != null,
                supportingText = state.repaymentAmountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AssistChip(
                onClick = { onEvent(LoanListEvent.RepaymentOpenDatePicker) },
                label = { Text(DateFormatters.fullDate(state.repaymentDate)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )

            if (state.paymentMethods.isNotEmpty()) {
                PaymentMethodChips(
                    methods = state.paymentMethods,
                    selectedId = state.repaymentPaymentMethodId,
                    onSelect = { onEvent(LoanListEvent.RepaymentPaymentMethodChanged(it)) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onEvent(LoanListEvent.RepaymentDismissed) }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = { onEvent(LoanListEvent.RepaymentSaved) },
                    enabled = state.canSaveRepayment,
                ) {
                    Text(stringResource(R.string.loan_repayment_save))
                }
            }
        }
    }

    if (state.repaymentShowDatePicker) {
        LoanDatePickerDialog(
            initial = state.repaymentDate,
            onSelect = { onEvent(LoanListEvent.RepaymentDateChanged(it)) },
            onDismiss = { onEvent(LoanListEvent.RepaymentDismissDatePicker) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentMethodChips(
    methods: List<PaymentMethod>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.loan_repayment_method_label),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            methods.forEach { method ->
                FilterChip(
                    selected = method.id == selectedId,
                    onClick = { onSelect(method.id) },
                    label = { Text(method.name) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Shared with the loan form. Reads the picker back as a UTC date, as elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoanDatePickerDialog(
    initial: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // The picker works in UTC millis; reading it back as a UTC date
                        // keeps the day the user tapped, whatever their zone.
                        onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun cardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

// -- Previews ---------------------------------------------------------------

private val previewCurrency = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)

private fun previewState(
    filter: LoanFilter = LoanFilter.OUTSTANDING,
    loading: Boolean = false,
    empty: Boolean = false,
): LoanListUiState {
    val today = LocalDate.of(2026, 9, 12)

    if (empty) {
        return LoanListUiState(
            isLoading = false,
            totalLoanCount = 0,
            summary = LoanSummary(0, 0, "PKR", 0, 0, emptyList()),
            currency = previewCurrency,
            today = today,
        )
    }

    val ali = Loan(
        id = "l1",
        counterparty = "Ali",
        direction = LoanDirection.LENT,
        principalMinor = 500_000,
        currencyCode = "PKR",
        date = LocalDate.of(2026, 8, 20),
        dueDate = LocalDate.of(2026, 9, 5),
        repayments = listOf(Repayment("r1", 200_000, LocalDate.of(2026, 9, 1), "pm-cash")),
    )
    val hina = Loan(
        id = "l2",
        counterparty = "Hina",
        direction = LoanDirection.BORROWED,
        principalMinor = 300_000,
        currencyCode = "PKR",
        date = LocalDate.of(2026, 9, 8),
    )

    return LoanListUiState(
        isLoading = loading,
        filter = filter,
        totalLoanCount = 2,
        rows = listOf(
            LoanRowState(ali, isOverdue = true),
            LoanRowState(hina, isOverdue = false),
        ),
        summary = LoanSummary(
            outstandingLentMinor = 300_000,
            outstandingBorrowedMinor = 300_000,
            currencyCode = "PKR",
            openCount = 2,
            overdueCount = 1,
            slices = listOf(
                LoanSlice("Ali", LoanDirection.LENT, 300_000, "PKR", 1f),
                LoanSlice("Hina", LoanDirection.BORROWED, 300_000, "PKR", 1f),
            ),
        ),
        currency = previewCurrency,
        today = today,
        paymentMethods = listOf(PaymentMethod("pm-cash", "Cash", "cash")),
    )
}

@Preview(name = "Loans", showBackground = true, heightDp = 900)
@Composable
private fun LoanListScreenPreview() {
    PaisaTheme {
        LoanListScreen(
            state = previewState(),
            onEvent = {},
            onAddLoan = {},
            onEditLoan = {},
            onBack = {},
        )
    }
}

@Preview(name = "Loans · dark", showBackground = true, heightDp = 900, uiMode = 32)
@Composable
private fun LoanListScreenDarkPreview() {
    PaisaTheme {
        LoanListScreen(
            state = previewState(),
            onEvent = {},
            onAddLoan = {},
            onEditLoan = {},
            onBack = {},
        )
    }
}

@Preview(name = "Loans · empty", showBackground = true, heightDp = 700)
@Composable
private fun LoanListScreenEmptyPreview() {
    PaisaTheme {
        LoanListScreen(
            state = previewState(empty = true),
            onEvent = {},
            onAddLoan = {},
            onEditLoan = {},
            onBack = {},
        )
    }
}

@Preview(name = "Loans · nothing settled", showBackground = true, heightDp = 700)
@Composable
private fun LoanListScreenNoSettledPreview() {
    PaisaTheme {
        LoanListScreen(
            state = previewState(filter = LoanFilter.SETTLED).copy(rows = emptyList()),
            onEvent = {},
            onAddLoan = {},
            onEditLoan = {},
            onBack = {},
        )
    }
}
