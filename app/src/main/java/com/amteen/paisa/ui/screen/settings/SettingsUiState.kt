package com.amteen.paisa.ui.screen.settings

import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.SortOrder
import com.amteen.paisa.domain.model.ThemeMode
import java.time.DayOfWeek

/**
 * What the settings screen renders.
 *
 * The whole of [AppSettings] rather than a field per control, because it is written
 * as one atomic object — a screen holding its own copies of each preference is how
 * two of them end up disagreeing.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val sampleDataBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val themeMode: ThemeMode get() = settings.themeMode
    val firstDayOfWeek: DayOfWeek get() = settings.firstDayOfWeek
    val defaultSortOrder: SortOrder get() = settings.defaultSortOrder
    val budgetAlertsEnabled: Boolean get() = settings.budgetAlertsEnabled
    val backupsToKeep: Int get() = settings.backupsToKeep
}

sealed interface SettingsEvent {
    data class ThemeChanged(val mode: ThemeMode) : SettingsEvent
    data class FirstDayOfWeekChanged(val day: DayOfWeek) : SettingsEvent
    data class DefaultSortChanged(val order: SortOrder) : SettingsEvent
    data class BudgetAlertsToggled(val enabled: Boolean) : SettingsEvent
    data class BackupsToKeepChanged(val count: Int) : SettingsEvent

    data object AddSampleData : SettingsEvent
    data object RemoveSampleData : SettingsEvent

    data object MessageShown : SettingsEvent
    data object DismissError : SettingsEvent
}
