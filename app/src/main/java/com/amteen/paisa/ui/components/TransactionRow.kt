package com.amteen.paisa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.AmountTextStyles

/**
 * One transaction in a list.
 *
 * Kept stateless and cheap: everything shown is already resolved on
 * [TransactionDetails] by a use case, so a scroll does no lookups. See CLAUDE.md.
 */
@Composable
fun TransactionRow(
    details: TransactionDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = false,
) {
    val categoryColor = details.category
        ?.let { Color(it.colorArgb) }
        ?: MaterialTheme.colorScheme.surfaceVariant

    val spoken = buildString {
        append(if (details.isExpense) "Spent " else "Received ")
        append(MoneyFormatter.format(details.money, details.currency))
        append(", ").append(details.title)
        append(", ").append(details.subtitle)
        details.paymentMethod?.let { append(", paid by ").append(it.name) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 48dp is the minimum comfortable touch target; the row is taller in
            // practice but must never fall below it on a compact display.
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            // The row speaks as one unit; reading five separate labels per row
            // makes a long list unusable with TalkBack.
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(categoryColor.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons[details.category?.iconKey],
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = details.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(details.subtitle)
                    if (showTime) append(" · ").append(DateFormatters.time(details.transaction.time))
                    details.paymentMethod?.let { append(" · ").append(it.name) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AmountText(
            money = details.money,
            currency = details.currency,
            type = details.transaction.type,
            style = AmountTextStyles.Row,
            modifier = Modifier.widthIn(min = MinAmountWidth),
        )
    }
}

/**
 * Amounts get a minimum column width so ordinary figures right-align down the list
 * and their decimal points line up — with the tabular figures in [AmountTextStyles],
 * a scrolling list then stays still instead of jittering.
 *
 * A *minimum* rather than a fixed width: a salary of Rs. 150,000.00 needs more room
 * than a Rs. 90.00 lunch, and a fixed column silently truncated it to "+Rs.". The
 * description beside it already ellipsises, so the amount is the right one to let
 * win the space.
 */
private val MinAmountWidth = 96.dp
