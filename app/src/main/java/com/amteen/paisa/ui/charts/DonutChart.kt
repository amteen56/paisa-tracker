package com.amteen.paisa.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.usecase.CategorySlice
import com.amteen.paisa.ui.theme.expenseColors
import kotlin.math.roundToInt

/**
 * The category breakdown, drawn as a ring.
 *
 * There is no charting dependency in this project — see CLAUDE.md — so the arcs are
 * `Canvas` draws. A ring rather than a filled pie because the middle is then free to
 * carry the total, which is the figure people actually want when they look at one of
 * these.
 *
 * **Colour is never the only signal.** Every slice is also a labelled legend row
 * carrying its name, amount and percentage, so the chart is readable in greyscale,
 * with any form of colour blindness, and by a screen reader — which gets the whole
 * breakdown as one spoken sentence rather than a shape it cannot see.
 */
@Composable
fun CategoryDonut(
    slices: List<CategorySlice>,
    currency: Currency,
    total: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    thickness: Dp = 26.dp,
    selectedCategoryId: String? = null,
    palette: List<Color> = MaterialTheme.expenseColors.chartSeries,
) {
    if (slices.isEmpty()) return

    val spoken = buildString {
        append("Spending by category. ")
        for (slice in slices) {
            append(slice.name)
            append(": ")
            append(MoneyFormatter.format(slice.amount, currency))
            append(", ")
            append((slice.share * 100).roundToInt())
            append("%. ")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The ring and its legend speak once, as a sentence. Reading eight
            // separate arc labels tells a screen reader user nothing they can hold on
            // to, and the ring itself conveys nothing at all.
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(diameter),
            contentAlignment = Alignment.Center,
        ) {
            Ring(
                slices = slices,
                palette = palette,
                thickness = thickness,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                selectedCategoryId = selectedCategoryId,
                modifier = Modifier.size(diameter),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Legend(slices = slices, currency = currency, palette = palette)
    }
}

@Composable
private fun Ring(
    slices: List<CategorySlice>,
    palette: List<Color>,
    thickness: Dp,
    trackColor: Color,
    selectedCategoryId: String?,
    modifier: Modifier = Modifier,
) {
    // One animation for the whole sweep rather than one per slice: the arcs share a
    // running start angle, so animating them independently would tear the ring apart
    // mid-transition.
    val progress by animateFloatAsState(targetValue = 1f, label = "categoryDonut")

    Canvas(modifier = modifier) {
        val stroke = thickness.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )

        // Start at twelve o'clock, clockwise — the direction people read a pie.
        var start = -90f
        val gap = if (slices.size > 1) SLICE_GAP_DEGREES else 0f

        slices.forEachIndexed { index, slice ->
            val full = slice.share * 360f
            // Never let a real amount vanish: below the gap width a slice would be
            // narrower than the space between slices and read as absent.
            val sweep = (full - gap).coerceAtLeast(if (slice.share > 0f) MIN_SLICE_DEGREES else 0f)
            if (sweep > 0f) {
                val dimmed = selectedCategoryId != null && slice.categoryId != selectedCategoryId
                drawArc(
                    color = palette[index % palette.size]
                        .let { if (dimmed) it.copy(alpha = 0.3f) else it },
                    startAngle = start,
                    sweepAngle = sweep * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            start += full
        }
    }
}

@Composable
private fun Legend(
    slices: List<CategorySlice>,
    currency: Currency,
    palette: List<Color>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        slices.forEachIndexed { index, slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = palette[index % palette.size],
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
                Text(
                    text = slice.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = "${(slice.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = MoneyFormatter.formatCompact(slice.amount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * A hairline between slices, so two adjacent categories of similar colour still read
 * as two categories.
 */
private const val SLICE_GAP_DEGREES = 2f

/** Floor for a non-zero slice, in degrees. */
private const val MIN_SLICE_DEGREES = 1.5f
