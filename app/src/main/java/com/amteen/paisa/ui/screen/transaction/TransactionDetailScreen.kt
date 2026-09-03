package com.amteen.paisa.ui.screen.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.ui.components.AmountText
import com.amteen.paisa.ui.components.ConfirmDialog
import com.amteen.paisa.ui.components.ErrorState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.expenseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    state: TransactionDetailUiState,
    onEvent: (TransactionDetailEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    state.details?.let { details ->
                        IconButton(onClick = { onEdit(details.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit transaction")
                        }
                        IconButton(onClick = { onEvent(TransactionDetailEvent.RequestDelete) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete transaction",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))

            state.details == null -> ErrorState(
                message = state.error ?: "That transaction could not be found.",
                modifier = Modifier.padding(padding),
                onRetry = { onEvent(TransactionDetailEvent.Reload) },
            )

            else -> DetailContent(
                details = state.details,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this transaction?",
            message = "This cannot be undone. Your other transactions are not affected.",
            confirmLabel = "Delete",
            onConfirm = { onEvent(TransactionDetailEvent.ConfirmDelete) },
            onDismiss = { onEvent(TransactionDetailEvent.DismissDelete) },
        )
    }
}

@Composable
private fun DetailContent(details: TransactionDetails, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.expenseColors
    val categoryColor = details.category?.let { Color(it.colorArgb) }
        ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(categoryColor.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons[details.category?.iconKey],
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        AmountText(
            money = details.money,
            currency = details.currency,
            type = details.transaction.type,
            style = AmountTextStyles.Hero,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (details.isExpense) "Expense" else "Income",
            style = MaterialTheme.typography.labelLarge,
            color = if (details.isExpense) colors.expense else colors.income,
        )

        if (details.transaction.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = details.transaction.description,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                DetailRow("Category", details.category?.name ?: "Uncategorised")
                details.subcategory?.let { DetailRow("Subcategory", it.name) }
                DetailRow("Date", DateFormatters.fullDate(details.transaction.date))
                DetailRow("Time", DateFormatters.time(details.transaction.time))
                details.paymentMethod?.let { DetailRow("Paid with", it.name) }
                DetailRow(
                    label = "Currency",
                    value = "${details.currency.code} — ${details.currency.name}",
                )
                if (details.transaction.currencyCode != details.currency.code) {
                    DetailRow("Amount", MoneyFormatter.format(details.money, details.currency))
                }
            }
        }

        if (!details.transaction.notes.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = details.transaction.notes,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
