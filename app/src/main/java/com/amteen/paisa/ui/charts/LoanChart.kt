package com.amteen.paisa.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.usecase.LoanSlice
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.expenseColors

/**
 * Who still owes what, as one bar per person.
 *
 * Bars rather than a ring, because the two directions are not slices of one whole:
 * money owed *to* the user and money owed *by* them are different kinds of thing, and
 * putting them in a single pie would suggest a total that means nothing. Each bar is
 * scaled against the largest balance, so the debt worth chasing is the longest one —
 * scaling against the sum would leave two similar loans both sitting near half width,
 * saying nothing about either.
 *
 * Colour carries the direction, and so does the word beside each amount: the row reads
 * correctly in both themes and to anyone who cannot tell the two tints apart.
 */
@Composable
fun OutstandingLoansChart(
    slices: List<LoanSlice>,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return

    val colors = MaterialTheme.expenseColors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slices.forEach { slice ->
            LoanBar(
                slice = slice,
                currency = currency,
                // Money coming back reads as income, money going out as expense —
                // the same two tints the rest of the app uses for the same idea.
                color = if (slice.direction.isLent) colors.income else colors.expense,
            )
        }
    }
}

@Composable
private fun LoanBar(
    slice: LoanSlice,
    currency: Currency,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val formatted = MoneyFormatter.format(slice.amount, currency)
    val spoken = stringResource(
        if (slice.direction.isLent) {
            R.string.loan_chart_spoken_lent
        } else {
            R.string.loan_chart_spoken_borrowed
        },
        slice.counterparty,
        formatted,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            // One spoken sentence per person. The bar is a picture of the number the
            // sentence already carries, so it has nothing of its own to announce.
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = slice.counterparty,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        DirectionBar(fraction = slice.share, color = color)
    }
}

/**
 * One horizontal bar on a track.
 *
 * Deliberately not `ShareBar`: that one is a fraction of a whole and rounds a tiny
 * share away to nothing, which is right for "3% of this month's spending" and wrong
 * for "Rs. 200 is still owed". A real balance always draws something.
 */
@Composable
private fun DirectionBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "loanBar")
    val track = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT.dp),
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = track,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
        // Floored at the bar's own height, so the shortest real balance is still a
        // visible pill rather than a sliver the user has to squint at.
        val width = (size.width * animated).coerceAtLeast(size.height)
        drawRoundRect(
            color = color,
            size = size.copy(width = width),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private const val BAR_HEIGHT = 8

// -- Previews ---------------------------------------------------------------

private fun previewSlices() = listOf(
    LoanSlice("Ali", LoanDirection.LENT, 500_000, "PKR", 1f),
    LoanSlice("Bilal", LoanDirection.LENT, 120_000, "PKR", 0.24f),
    LoanSlice("Hina", LoanDirection.BORROWED, 300_000, "PKR", 0.6f),
    LoanSlice("Zara", LoanDirection.LENT, 2_000, "PKR", 0.004f),
)

@Preview(name = "Outstanding loans", showBackground = true, widthDp = 340)
@Composable
private fun OutstandingLoansChartPreview() {
    PaisaTheme {
        OutstandingLoansChart(
            slices = previewSlices(),
            currency = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0),
        )
    }
}

@Preview(name = "Outstanding loans · dark", showBackground = true, widthDp = 340, uiMode = 32)
@Composable
private fun OutstandingLoansChartDarkPreview() {
    PaisaTheme {
        OutstandingLoansChart(
            slices = previewSlices(),
            currency = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0),
        )
    }
}
