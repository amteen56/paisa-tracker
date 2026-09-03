package com.amteen.paisa.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.ui.theme.AmountTextStyles
import com.amteen.paisa.ui.theme.expenseColors

/**
 * Renders an amount.
 *
 * Composables must never assemble a money string themselves — this is the only
 * bridge between [MoneyFormatter] and the UI, so precision, grouping and symbol
 * placement stay consistent everywhere. See CLAUDE.md.
 *
 * Colour carries meaning here, so it is never the *only* carrier: an income figure
 * is green **and** prefixed with `+`, an expense red **and** prefixed with `-`.
 * Red/green alone is unreadable to a large minority of users.
 */
@Composable
fun AmountText(
    money: Money,
    currency: Currency,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    style: TextStyle = AmountTextStyles.Row,
    showSign: Boolean = type != null,
    color: Color? = null,
) {
    val colors = MaterialTheme.expenseColors
    val resolvedColor = color ?: when (type) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> colors.expense
        null -> MaterialTheme.colorScheme.onSurface
    }

    val formatted = MoneyFormatter.format(money, currency)
    val text = when {
        !showSign -> formatted
        type == TransactionType.INCOME -> "+$formatted"
        type == TransactionType.EXPENSE -> "-$formatted"
        else -> formatted
    }

    Text(
        text = text,
        style = style,
        color = resolvedColor,
        maxLines = 1,
        softWrap = false,
        // If an amount ever does run out of room, it must *say so* rather than
        // silently truncating: "+Rs." read as a whole number is far worse than
        // "+Rs. 150,0…", which is visibly incomplete.
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics {
            // TalkBack should say "spent 350 rupees 50", not read a bare number
            // stripped of the sign that gives it meaning.
            contentDescription = when (type) {
                TransactionType.INCOME -> "Received $formatted"
                TransactionType.EXPENSE -> "Spent $formatted"
                null -> formatted
            }
        },
    )
}

/**
 * A net figure, where the sign comes from the value rather than a transaction type.
 * Used for balances and day totals, which can go either way.
 */
@Composable
fun NetAmountText(
    money: Money,
    currency: Currency,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountTextStyles.Row,
) {
    val colors = MaterialTheme.expenseColors
    AmountText(
        money = money.abs(),
        currency = currency,
        modifier = modifier,
        type = if (money.isNegative) TransactionType.EXPENSE else TransactionType.INCOME,
        style = style,
        showSign = !money.isZero,
        color = when {
            money.isZero -> MaterialTheme.colorScheme.onSurfaceVariant
            money.isNegative -> colors.expense
            else -> colors.income
        },
    )
}
