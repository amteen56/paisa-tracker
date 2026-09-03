package com.amteen.paisa.ui.screen.transaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add or edit a transaction.
 *
 * The whole layout is arranged around one number: recording "Rs. 800, Food / Fast
 * Food, Burger" should take five to ten seconds. So the amount field is first and
 * autofocused, the keypad opens on its own, categories are one-tap chips rather
 * than a dropdown, the date defaults to today, and notes stay collapsed until
 * asked for. Anything that adds a tap to that path has to earn it — see CLAUDE.md.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTransactionScreen(
    state: AddEditTransactionUiState,
    onEvent: (AddEditTransactionEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val amountFocus = remember { FocusRequester() }

    // Pop only once the write has actually reached disk.
    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    LaunchedEffect(state.isLoading, state.isEditing) {
        // Autofocus on Add so the keypad is already up. On Edit the user is more
        // likely to be changing a category or a date, and stealing focus would
        // cover half the form with a keyboard they did not ask for.
        if (!state.isLoading && !state.isEditing) amountFocus.requestFocus()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(AddEditTransactionEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.isEditing -> "Edit transaction"
                            state.type.isIncome -> "Add income"
                            else -> "Add expense"
                        },
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            TypeSelector(type = state.type, onSelect = { onEvent(AddEditTransactionEvent.TypeChanged(it)) })

            AmountField(
                state = state,
                focusRequester = amountFocus,
                onAmountChange = { onEvent(AddEditTransactionEvent.AmountChanged(it)) },
                onCurrencyChange = { onEvent(AddEditTransactionEvent.CurrencySelected(it)) },
            )

            Section(title = "Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { category ->
                        val selected = category.id == state.selectedCategoryId
                        FilterChip(
                            selected = selected,
                            onClick = { onEvent(AddEditTransactionEvent.CategorySelected(category.id)) },
                            label = { Text(category.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = CategoryIcons[category.iconKey],
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        Color(category.colorArgb)
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.subcategories.isNotEmpty()) {
                Section(title = "Subcategory", subtitle = "Optional") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.subcategories.forEach { subcategory ->
                            FilterChip(
                                selected = subcategory.id == state.selectedSubcategoryId,
                                onClick = {
                                    onEvent(AddEditTransactionEvent.SubcategorySelected(subcategory.id))
                                },
                                label = { Text(subcategory.name) },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = { onEvent(AddEditTransactionEvent.DescriptionChanged(it)) },
                label = { Text("Description") },
                placeholder = { Text("Burger") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            DateSelector(
                date = state.date,
                onSelect = { onEvent(AddEditTransactionEvent.DateSelected(it)) },
                onPick = { onEvent(AddEditTransactionEvent.OpenDatePicker) },
            )

            if (state.paymentMethods.isNotEmpty()) {
                Section(title = "Paid with", subtitle = "Optional") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.paymentMethods.forEach { method ->
                            FilterChip(
                                selected = method.id == state.selectedPaymentMethodId,
                                onClick = {
                                    onEvent(AddEditTransactionEvent.PaymentMethodSelected(method.id))
                                },
                                label = { Text(method.name) },
                            )
                        }
                    }
                }
            }

            if (state.notesVisible) {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onEvent(AddEditTransactionEvent.NotesChanged(it)) },
                    label = { Text("Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AssistChip(
                    onClick = { onEvent(AddEditTransactionEvent.ToggleNotes) },
                    label = { Text("Add a note") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            Modifier.size(18.dp),
                        )
                    },
                )
            }

            Button(
                onClick = { onEvent(AddEditTransactionEvent.Save) },
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (state.isEditing) "Save changes" else "Save", fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.showDatePicker) {
            DatePickerSheet(
                initial = state.date,
                onSelect = { onEvent(AddEditTransactionEvent.DateSelected(it)) },
                onDismiss = { onEvent(AddEditTransactionEvent.DismissDatePicker) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(type: TransactionType, onSelect: (TransactionType) -> Unit) {
    val colors = MaterialTheme.expenseColors
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TransactionType.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = type == entry,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = if (entry.isIncome) {
                        colors.incomeContainer
                    } else {
                        colors.expenseContainer
                    },
                    activeContentColor = if (entry.isIncome) {
                        colors.onIncomeContainer
                    } else {
                        colors.onExpenseContainer
                    },
                ),
            ) {
                Text(if (entry.isIncome) "Income" else "Expense")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountField(
    state: AddEditTransactionUiState,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    var currencyMenuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.expenseColors
    val accent = if (state.type.isIncome) colors.income else colors.expense

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { currencyMenuOpen = true }) {
                    Text(state.currency.code, fontWeight = FontWeight.SemiBold)
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Change currency",
                        Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = currencyMenuOpen,
                    onDismissRequest = { currencyMenuOpen = false },
                ) {
                    state.currencies.forEach { currency ->
                        DropdownMenuItem(
                            text = { Text("${currency.code} — ${currency.name}") },
                            onClick = {
                                onCurrencyChange(currency.code)
                                currencyMenuOpen = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.amountInput,
                onValueChange = onAmountChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("0", fontSize = 28.sp) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accent,
                ),
                singleLine = true,
                isError = state.amountError != null,
                supportingText = state.amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    // Decimal rather than Number, so the keypad shows a decimal
                    // point — except where the currency has no minor units.
                    keyboardType = if (state.currency.decimalDigits > 0) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    },
                    imeAction = ImeAction.Next,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSelector(
    date: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPick: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    Section(title = "Date") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = date == today,
                onClick = { onSelect(today) },
                label = { Text("Today") },
            )
            FilterChip(
                selected = date == today.minusDays(1),
                onClick = { onSelect(today.minusDays(1)) },
                label = { Text("Yesterday") },
            )
            FilterChip(
                selected = date != today && date != today.minusDays(1),
                onClick = onPick,
                label = {
                    Text(
                        if (date == today || date == today.minusDays(1)) {
                            "Pick a date"
                        } else {
                            com.amteen.paisa.core.time.DateFormatters.fullDate(date)
                        },
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, Modifier.size(16.dp))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
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
                        // The picker works in UTC millis; reading it back as a UTC
                        // date keeps the day the user tapped, whatever their zone.
                        onSelect(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                },
            ) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = "  $subtitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

// -- Previews ---------------------------------------------------------------

@Preview(name = "Add expense — light", showBackground = true)
@Composable
private fun AddExpensePreview() {
    PaisaTheme {
        AddEditTransactionScreen(
            state = previewState(),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Add expense — dark", showBackground = true, uiMode = 32)
@Composable
private fun AddExpenseDarkPreview() {
    PaisaTheme {
        AddEditTransactionScreen(
            state = previewState(),
            onEvent = {},
            onBack = {},
        )
    }
}

private fun previewState() = AddEditTransactionUiState(
    isLoading = false,
    amountInput = "800",
    currency = com.amteen.paisa.data.seed.DefaultData.currencies.first(),
    currencies = com.amteen.paisa.data.seed.DefaultData.currencies,
    categories = com.amteen.paisa.data.seed.DefaultData.categories
        .filter { it.applicableTo.allows(TransactionType.EXPENSE) },
    selectedCategoryId = "cat-food",
    description = "Burger",
    paymentMethods = com.amteen.paisa.data.seed.DefaultData.paymentMethods,
    selectedPaymentMethodId = "pm-cash",
)
