package com.amteen.paisa.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.money.MoneyFormatter
import com.amteen.paisa.domain.model.AverageFilterMode
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.usecase.DashboardSummary
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.PaisaTheme

/**
 * Which categories the daily average counts.
 *
 * The figure at the top is the live one from [DashboardSummary] — the same value the
 * tile behind this dialog shows. Every tap writes to settings, the use case recomputes,
 * and the number here moves. Showing a preview computed locally would have meant the
 * dialog doing arithmetic, which is exactly what CLAUDE.md forbids, and it would have
 * been a second implementation of the average to keep in step.
 *
 * Exclude is the default direction because it is the one that answers "what does an
 * ordinary day cost me?" — a handful of lumpy categories out, everything else in.
 */
@Composable
fun DailyAverageDialog(
    summary: DashboardSummary,
    categories: List<Category>,
    mode: AverageFilterMode,
    selected: Set<String>,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss = { onEvent(HomeEvent.AverageFilterDismissed) }

    AlertDialog(
        onDismissRequest = dismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.average_filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AverageHeadline(summary = summary)

                if (categories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.average_filter_no_categories),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }

                ModeChips(mode = mode, onEvent = onEvent)

                Text(
                    text = when {
                        selected.isEmpty() -> stringResource(R.string.average_filter_none)
                        mode == AverageFilterMode.INCLUDE ->
                            stringResource(R.string.average_filter_hint_include)
                        else -> stringResource(R.string.average_filter_hint_exclude)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Bounded, because someone with thirty categories must still be able to
                // reach the buttons underneath.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = categories, key = { it.id }) { category ->
                        CategoryCheckRow(
                            category = category,
                            checked = category.id in selected,
                            onToggle = {
                                onEvent(HomeEvent.AverageFilterCategoryToggled(category.id))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(R.string.average_filter_done))
            }
        },
        dismissButton = {
            // Nothing to clear when nothing is ticked, and an inert button beside
            // "Done" only invites a pointless tap.
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onEvent(HomeEvent.AverageFilterCleared) }) {
                    Text(stringResource(R.string.average_filter_clear))
                }
            }
        },
    )
}

/** The live average, so the effect of a tap is visible without closing the dialog. */
@Composable
private fun AverageHeadline(summary: DashboardSummary, modifier: Modifier = Modifier) {
    val amount = MoneyFormatter.format(summary.dailyAverage, summary.baseCurrency)
    val days = pluralStringResource(
        R.plurals.home_days_in,
        summary.averageDays,
        summary.averageDays,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$amount. $days." },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = amount, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = days,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(
    mode: AverageFilterMode,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.average_filter_mode_label),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AverageFilterMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode,
                    onClick = { onEvent(HomeEvent.AverageFilterModeChanged(option)) },
                    label = {
                        Text(
                            when (option) {
                                AverageFilterMode.EXCLUDE ->
                                    stringResource(R.string.average_filter_mode_exclude)
                                AverageFilterMode.INCLUDE ->
                                    stringResource(R.string.average_filter_mode_include)
                            },
                        )
                    },
                    // Chips are under 48dp by default — see CLAUDE.md.
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryCheckRow(
    category: Category,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(category.colorArgb)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // One control, one stop: the icon, name and checkbox are one setting.
            .clearAndSetSemantics {
                contentDescription =
                    "${category.name}, ${if (checked) "selected" else "not selected"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons[category.iconKey],
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

// -- Previews ---------------------------------------------------------------

private fun previewCategories() = listOf(
    Category("cat-food", "Food", CategoryScope.EXPENSE, "restaurant", 0xFFE07A5F.toInt()),
    Category("cat-bills", "Bills", CategoryScope.EXPENSE, "receipt", 0xFF3F6BB5.toInt()),
    Category("cat-rent", "Rent", CategoryScope.EXPENSE, "home", 0xFF6A8D73.toInt()),
    Category("cat-transport", "Transport", CategoryScope.EXPENSE, "car", 0xFFB5836A.toInt()),
)

@Preview(name = "Daily average · exclude", showBackground = true)
@Composable
private fun DailyAverageDialogPreview() {
    PaisaTheme {
        DailyAverageDialog(
            summary = previewSummary(),
            categories = previewCategories(),
            mode = AverageFilterMode.EXCLUDE,
            selected = setOf("cat-bills", "cat-rent"),
            onEvent = {},
        )
    }
}

@Preview(name = "Daily average · dark", showBackground = true, uiMode = 32)
@Composable
private fun DailyAverageDialogDarkPreview() {
    PaisaTheme {
        DailyAverageDialog(
            summary = previewSummary(),
            categories = previewCategories(),
            mode = AverageFilterMode.INCLUDE,
            selected = setOf("cat-food"),
            onEvent = {},
        )
    }
}

@Preview(name = "Daily average · nothing selected", showBackground = true)
@Composable
private fun DailyAverageDialogUnfilteredPreview() {
    PaisaTheme {
        DailyAverageDialog(
            summary = previewSummary(),
            categories = previewCategories(),
            mode = AverageFilterMode.EXCLUDE,
            selected = emptySet(),
            onEvent = {},
        )
    }
}

@Preview(name = "Daily average · no categories", showBackground = true)
@Composable
private fun DailyAverageDialogNoCategoriesPreview() {
    PaisaTheme {
        DailyAverageDialog(
            summary = previewSummary(),
            categories = emptyList(),
            mode = AverageFilterMode.EXCLUDE,
            selected = emptySet(),
            onEvent = {},
        )
    }
}
