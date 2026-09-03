package com.amteen.paisa.ui.screen.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.ui.navigation.Routes

private data class MoreEntry(
    val titleRes: Int,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

private data class MoreSection(val header: String, val entries: List<MoreEntry>)

private val sections = listOf(
    MoreSection(
        header = "Finance",
        entries = listOf(
            MoreEntry(
                R.string.title_budgets,
                "Set monthly limits and track usage",
                Icons.Outlined.Savings,
                Routes.BUDGETS,
            ),
            MoreEntry(
                R.string.title_calendar,
                "See spending day by day",
                Icons.Outlined.CalendarMonth,
                Routes.CALENDAR,
            ),
        ),
    ),
    MoreSection(
        header = "Setup",
        entries = listOf(
            MoreEntry(
                R.string.title_categories,
                "Categories and subcategories",
                Icons.Outlined.Category,
                Routes.CATEGORIES,
            ),
            MoreEntry(
                R.string.title_payment_methods,
                "Cash, bank, cards, wallets",
                Icons.Outlined.Payments,
                Routes.PAYMENT_METHODS,
            ),
        ),
    ),
    MoreSection(
        header = "Data",
        entries = listOf(
            MoreEntry(
                R.string.title_backup,
                "JSON backup, CSV export and import",
                Icons.Outlined.SwapHoriz,
                Routes.BACKUP,
            ),
        ),
    ),
    MoreSection(
        header = "App",
        entries = listOf(
            MoreEntry(
                R.string.title_settings,
                "Theme, notifications, defaults",
                Icons.Outlined.Settings,
                Routes.SETTINGS,
            ),
            MoreEntry(
                R.string.title_about,
                "Version and privacy",
                Icons.Outlined.Info,
                Routes.ABOUT,
            ),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_more)) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            sections.forEach { section ->
                item(key = "header-${section.header}") {
                    Text(
                        text = section.header,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 20.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
                items(section.entries, key = { it.route }) { entry ->
                    ListItem(
                        headlineContent = { Text(stringResource(entry.titleRes)) },
                        supportingContent = { Text(entry.subtitle) },
                        leadingContent = {
                            Icon(entry.icon, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable { onNavigate(entry.route) },
                    )
                }
                item(key = "divider-${section.header}") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "privacy") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.privacy_statement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
