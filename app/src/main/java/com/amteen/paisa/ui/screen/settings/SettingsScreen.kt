package com.amteen.paisa.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.domain.model.SortOrder
import com.amteen.paisa.domain.model.ThemeMode
import com.amteen.paisa.domain.model.TransactionSort
import com.amteen.paisa.domain.usecase.SeedSampleDataUseCase
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.theme.PaisaTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Settings.
 *
 * These preferences all existed in `settings.json` from Phase 2 and had no way to
 * change them — this screen is the missing half, not new state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(SettingsEvent.MessageShown)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(SettingsEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "appearance") {
                // Resolved here rather than inside the chip lambda: stringResource is
                // itself a composable call and cannot run from a plain function.
                val themeLabels = mapOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                )
                SettingsCard(stringResource(R.string.settings_appearance)) {
                    ChipRow(
                        label = stringResource(R.string.settings_theme),
                        options = ThemeMode.entries.toList(),
                        selected = state.themeMode,
                        labelFor = { themeLabels.getValue(it) },
                        onSelect = { onEvent(SettingsEvent.ThemeChanged(it)) },
                    )
                }
            }

            item(key = "dates") {
                SettingsCard(stringResource(R.string.settings_dates)) {
                    ChipRow(
                        label = stringResource(R.string.settings_first_day),
                        // Only the three that people actually start a week on. All
                        // seven would be a wall of chips for no real gain.
                        options = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                        selected = state.firstDayOfWeek,
                        labelFor = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                        onSelect = { onEvent(SettingsEvent.FirstDayOfWeekChanged(it)) },
                    )
                    Text(
                        text = stringResource(R.string.settings_first_day_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "lists") {
                SettingsCard(stringResource(R.string.settings_lists)) {
                    ChipRow(
                        label = stringResource(R.string.settings_default_sort),
                        options = SortOrder.entries.toList(),
                        selected = state.defaultSortOrder,
                        labelFor = { TransactionSort.from(it).label },
                        onSelect = { onEvent(SettingsEvent.DefaultSortChanged(it)) },
                    )
                }
            }

            item(key = "alerts") {
                SettingsCard(stringResource(R.string.settings_alerts)) {
                    SwitchRow(
                        label = stringResource(R.string.budget_alerts_label),
                        checked = state.budgetAlertsEnabled,
                        onToggle = { onEvent(SettingsEvent.BudgetAlertsToggled(it)) },
                    )
                    Text(
                        text = stringResource(R.string.budget_alerts_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "backups") {
                SettingsCard(stringResource(R.string.settings_backups)) {
                    ChipRow(
                        label = stringResource(R.string.settings_backups_keep),
                        options = listOf(3, 5, 10, 20),
                        selected = state.backupsToKeep,
                        labelFor = { it.toString() },
                        onSelect = { onEvent(SettingsEvent.BackupsToKeepChanged(it)) },
                    )
                    Text(
                        text = stringResource(R.string.settings_backups_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "sample") {
                SettingsCard(stringResource(R.string.settings_sample_title)) {
                    Text(
                        text = stringResource(
                            R.string.settings_sample_body,
                            SeedSampleDataUseCase.DEFAULT_MONTHS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.sampleDataBusy) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(R.string.settings_sample_busy),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEvent(SettingsEvent.AddSampleData) }) {
                                Text(stringResource(R.string.settings_sample_add))
                            }
                            TextButton(onClick = { onEvent(SettingsEvent.RemoveSampleData) }) {
                                Text(stringResource(R.string.settings_sample_remove))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_sample_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "privacy") {
                Text(
                    text = stringResource(R.string.privacy_statement),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

/**
 * A labelled row of single-choice chips.
 *
 * Chips rather than a dropdown: every one of these has three or four options, and a
 * dropdown hides the choices behind a tap while also being harder to reach with
 * TalkBack. Each chip states its own selected state, so a screen reader announces
 * "selected" rather than leaving the user to infer it from a colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val text = labelFor(option)
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text) },
                    // 48dp so the target is comfortable, which chips are not by
                    // default — see CLAUDE.md.
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // The row speaks as one control; a separate label and switch is two
            // stops for one setting.
            .clearAndSetSemantics {
                contentDescription = "$label, ${if (checked) "on" else "off"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

// -- Previews ---------------------------------------------------------------

@Preview(name = "Settings", showBackground = true, heightDp = 1100)
@Composable
private fun SettingsPreview() {
    PaisaTheme {
        SettingsScreen(
            state = SettingsUiState(isLoading = false),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Settings · dark", showBackground = true, heightDp = 1100, uiMode = 32)
@Composable
private fun SettingsDarkPreview() {
    PaisaTheme {
        SettingsScreen(
            state = SettingsUiState(isLoading = false),
            onEvent = {},
            onBack = {},
        )
    }
}
