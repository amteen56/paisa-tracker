package com.amteen.paisa.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.usecase.DailySpend
import com.amteen.paisa.ui.theme.expenseColors
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seven days of spending, drawn by hand.
 *
 * There is no charting dependency in this project — see CLAUDE.md — so the bars are
 * `Canvas` draws. Each bar is its own small canvas inside a `Row` rather than one
 * canvas covering the whole chart: the day labels then lay themselves out with real
 * text layout instead of hand-measured glyph positions, and each bar can hold its
 * own animation without the chart caring how many bars there are.
 *
 * Heights are relative to the busiest day in the window, so the shape of a week is
 * readable whether the user spends hundreds or hundreds of thousands. That also
 * means the chart shows **proportion, not magnitude** — the figures beside it carry
 * the actual amounts, and the whole thing has a spoken alternative because a bar
 * chart conveys nothing to a screen reader.
 */
@Composable
fun DailySpendBars(
    days: List<DailySpend>,
    currency: Currency,
    modifier: Modifier = Modifier,
    barHeight: Dp = 96.dp,
) {
    if (days.isEmpty()) return

    val colors = MaterialTheme.expenseColors
    val track = MaterialTheme.colorScheme.surfaceVariant
    val peak = days.maxOf { it.amountMinor }

    val spoken = buildString {
        append("Spending over the last ${days.size} days. ")
        for (day in days) {
            append(day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            append(": ")
            append(
                if (day.amountMinor == 0L) "nothing"
                else MoneyFormatter.format(day.amount, currency),
            )
            append(". ")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The chart speaks once, as a sentence. Seven separate bar labels read
            // in sequence tell a TalkBack user nothing they can hold on to.
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val isToday = index == days.lastIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DayBar(
                    // A day with nothing on it still gets a visible sliver, so an
                    // empty day and a missing bar cannot look like the same thing.
                    fraction = if (peak <= 0L) 0f else day.amountMinor.toFloat() / peak.toFloat(),
                    color = if (isToday) colors.expense else colors.expense.copy(alpha = 0.55f),
                    trackColor = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = day.date.dayOfWeek
                        .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * One bar, filling upward from the baseline.
 *
 * The fill animates so a change in the underlying week is visible as movement
 * rather than a silent redraw.
 */
@Composable
private fun DayBar(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "dailySpendBar",
    )

    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.width / 2f, size.width / 2f)

        drawRoundRect(
            color = trackColor,
            cornerRadius = corner,
        )

        // Never let a real amount round away to nothing: below this the bar would
        // be shorter than its own corner radius and read as empty.
        val minimum = if (fraction > 0f) size.width else 0f
        val filled = maxOf(size.height * animated, minimum)
        if (filled <= 0f) return@Canvas

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height - filled),
            size = Size(size.width, filled),
            cornerRadius = corner,
        )
    }
}

/**
 * A horizontal share bar for the top-categories list.
 *
 * Also hand-drawn, for the same reason, and also animated so a category overtaking
 * another is something the user can see happen.
 */
@Composable
fun ShareBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "categoryShareBar",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = corner)

        val filled = maxOf(size.width * animated, if (fraction > 0f) size.height else 0f)
        if (filled <= 0f) return@Canvas
        drawRoundRect(
            color = color,
            size = Size(filled, size.height),
            cornerRadius = corner,
        )
    }
}
