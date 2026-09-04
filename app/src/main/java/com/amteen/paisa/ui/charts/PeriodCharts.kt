package com.amteen.paisa.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.usecase.DailyPoint
import com.amteen.paisa.domain.usecase.MonthlyPoint
import com.amteen.paisa.ui.theme.expenseColors

/**
 * Spending day by day across the report period, drawn by hand.
 *
 * One canvas for the whole row of bars rather than one per bar — unlike the
 * dashboard's seven-day chart, this can carry sixty days, and sixty composables each
 * with their own animation is a lot of machinery to draw sixty rectangles.
 *
 * Bars are relative to the busiest day, so the shape of a period reads the same
 * whether the user spends hundreds or hundreds of thousands. That means the chart
 * shows **proportion, not magnitude** — the axis labels either side carry the actual
 * figures, and the whole thing has a spoken alternative because a bar chart conveys
 * nothing to a screen reader.
 */
@Composable
fun DailyExpenseBars(
    days: List<DailyPoint>,
    currency: Currency,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    if (days.isEmpty()) return

    val colors = MaterialTheme.expenseColors
    val track = MaterialTheme.colorScheme.surfaceVariant
    val peak = days.maxOf { it.expenseMinor }
    val busiest = days.filter { it.expenseMinor > 0L }.maxByOrNull { it.expenseMinor }
    val spentDays = days.count { it.expenseMinor > 0L }

    val spoken = buildString {
        append("Daily spending from ")
        append(DateFormatters.compactDate(days.first().date))
        append(" to ")
        append(DateFormatters.compactDate(days.last().date))
        append(". Money went out on ")
        append(spentDays)
        append(" of ")
        append(days.size)
        append(" days. ")
        if (busiest != null) {
            append("Highest was ")
            append(DateFormatters.compactDate(busiest.date))
            append(" at ")
            append(MoneyFormatter.format(busiest.expense, currency))
            append(".")
        }
    }

    val progress by animateFloatAsState(targetValue = 1f, label = "dailyExpenseBars")

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Sixty bars read out one at a time is unusable. The sentence gives the
            // span, how many days had spending, and the peak — which is what the
            // shape of the chart is actually telling a sighted user.
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        if (peak > 0L) {
            Text(
                text = MoneyFormatter.formatCompact(Money(peak, currency.code), currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (days.isEmpty()) return@Canvas

            // A gap proportional to the bar width, so a 60-day chart stays legible
            // instead of turning into one solid block.
            val slot = size.width / days.size
            val gap = (slot * BAR_GAP_FRACTION).coerceAtMost(MAX_BAR_GAP_PX)
            val barWidth = (slot - gap).coerceAtLeast(1f)
            val corner = CornerRadius(minOf(barWidth / 2f, 3f))

            days.forEachIndexed { index, day ->
                val left = index * slot + gap / 2f
                val fraction = if (peak <= 0L) 0f else day.expenseMinor.toFloat() / peak.toFloat()

                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, size.height - TRACK_HEIGHT_PX),
                    size = Size(barWidth, TRACK_HEIGHT_PX),
                    cornerRadius = corner,
                )

                if (fraction <= 0f) return@forEachIndexed
                // Never let a real amount round away to an invisible bar.
                val filled = maxOf(size.height * fraction * progress, MIN_BAR_HEIGHT_PX)
                drawRoundRect(
                    color = colors.expense,
                    topLeft = Offset(left, size.height - filled),
                    size = Size(barWidth, filled),
                    cornerRadius = corner,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = DateFormatters.compactDate(days.first().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = DateFormatters.compactDate(days.last().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Income against expense, month by month.
 *
 * Two series on one set of axes. They are told apart by **three** things, not just
 * colour: income is a solid line with filled dots, expense a dashed line with hollow
 * ones, and both are named in the legend underneath. A reader who cannot separate the
 * two hues still has the dash pattern and the marker shape.
 */
@Composable
fun IncomeExpenseTrend(
    months: List<MonthlyPoint>,
    currency: Currency,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    if (months.size < 2) return

    val colors = MaterialTheme.expenseColors
    val grid = MaterialTheme.colorScheme.outlineVariant
    val peak = months.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }

    val spoken = buildString {
        append("Income against spending, ")
        append(DateFormatters.monthShort(months.first().month))
        append(" to ")
        append(DateFormatters.monthShort(months.last().month))
        append(". ")
        for (point in months) {
            append(DateFormatters.monthShort(point.month))
            append(": in ")
            append(MoneyFormatter.format(point.income, currency))
            append(", out ")
            append(MoneyFormatter.format(point.expense, currency))
            append(". ")
        }
    }

    val progress by animateFloatAsState(targetValue = 1f, label = "incomeExpenseTrend")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        if (peak > 0L) {
            Text(
                text = MoneyFormatter.formatCompact(Money(peak, currency.code), currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val inset = MARKER_RADIUS_PX * 2f
            val usableHeight = size.height - inset * 2f
            val step = if (months.size > 1) size.width / (months.size - 1) else 0f

            // A baseline and a midline. Any more gridlines and the two series stop
            // being the thing the eye lands on first.
            for (fraction in listOf(0f, 0.5f, 1f)) {
                val y = inset + usableHeight * fraction
                drawLine(
                    color = grid.copy(alpha = if (fraction == 1f) 0.9f else 0.4f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            fun yFor(value: Long): Float {
                val fraction = if (peak <= 0L) 0f else value.toFloat() / peak.toFloat()
                return inset + usableHeight * (1f - fraction * progress)
            }

            fun series(values: List<Long>, color: Color, dashed: Boolean, filledMarkers: Boolean) {
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = index * step
                    val y = yFor(value)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = LINE_WIDTH_PX,
                        cap = StrokeCap.Round,
                        pathEffect = if (dashed) {
                            PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        } else {
                            null
                        },
                    ),
                )
                values.forEachIndexed { index, value ->
                    val centre = Offset(index * step, yFor(value))
                    if (filledMarkers) {
                        drawCircle(color = color, radius = MARKER_RADIUS_PX, center = centre)
                    } else {
                        // A ring, not a punched-out disc: a stroked circle is the
                        // same shape without needing a separate layer to blend into.
                        drawCircle(
                            color = color,
                            radius = MARKER_RADIUS_PX,
                            center = centre,
                            style = Stroke(width = MARKER_STROKE_PX),
                        )
                    }
                }
            }

            series(months.map { it.expenseMinor }, colors.expense, dashed = true, filledMarkers = false)
            series(months.map { it.incomeMinor }, colors.income, dashed = false, filledMarkers = true)
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            months.forEach { point ->
                Text(
                    text = DateFormatters.monthShort(point.month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendKey(label = "Income", color = colors.income, dashed = false)
            LegendKey(label = "Spent", color = colors.expense, dashed = true)
        }
    }
}

@Composable
private fun LegendKey(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The key repeats the line's own dash pattern, so the legend and the chart
        // are matched by shape rather than by colour alone.
        Canvas(
            modifier = Modifier
                .width(18.dp)
                .height(10.dp),
        ) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = LINE_WIDTH_PX,
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * A share bar with a coloured swatch and a label, for the category and subcategory
 * lists under the charts.
 */
@Composable
fun LabelledShareRow(
    label: String,
    value: String,
    share: Float,
    color: Color,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
        ShareBar(fraction = share, color = color)
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val BAR_GAP_FRACTION = 0.25f
private const val MAX_BAR_GAP_PX = 6f
private const val MIN_BAR_HEIGHT_PX = 3f
private const val TRACK_HEIGHT_PX = 2f
private const val LINE_WIDTH_PX = 3f
private const val MARKER_RADIUS_PX = 4f
private const val MARKER_STROKE_PX = 2f
