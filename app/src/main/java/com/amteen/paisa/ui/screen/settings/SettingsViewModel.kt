package com.amteen.paisa.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.ClearSampleDataUseCase
import com.amteen.paisa.domain.usecase.SeedSampleDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings.
 *
 * Every change is a single `settings.update { }`, which is one atomic file write —
 * there is no local draft to get out of step with what is on disk.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val seedSampleData: SeedSampleDataUseCase,
    private val clearSampleData: ClearSampleDataUseCase,
) : ViewModel() {

    private val chrome = MutableStateFlow(Chrome())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        chrome,
    ) { settings, flags ->
        SettingsUiState(
            isLoading = false,
            settings = settings,
            sampleDataBusy = flags.sampleDataBusy,
            message = flags.message,
            error = flags.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        viewModelScope.launch { settingsRepository.load() }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ThemeChanged -> update { it.copy(themeMode = event.mode) }

            is SettingsEvent.FirstDayOfWeekChanged ->
                update { it.copy(firstDayOfWeek = event.day) }

            is SettingsEvent.DefaultSortChanged ->
                update { it.copy(defaultSortOrder = event.order) }

            is SettingsEvent.BudgetAlertsToggled ->
                update { it.copy(budgetAlertsEnabled = event.enabled) }

            is SettingsEvent.BackupsToKeepChanged ->
                // Clamped here rather than trusted from the UI: zero would disable
                // the rolling snapshot silently, and a huge number would let the
                // backup folder grow without bound.
                update { it.copy(backupsToKeep = event.count.coerceIn(1, 20)) }

            SettingsEvent.AddSampleData -> runSampleData {
                when (val result = seedSampleData()) {
                    is AppResult.Ok -> "Added ${result.value} sample transactions." to null
                    is AppResult.Err -> null to result.error.displayMessage
                }
            }

            SettingsEvent.RemoveSampleData -> runSampleData {
                when (val result = clearSampleData()) {
                    is AppResult.Ok -> "Removed ${result.value} sample transactions." to null
                    is AppResult.Err -> null to result.error.displayMessage
                }
            }

            SettingsEvent.MessageShown -> chrome.update { it.copy(message = null) }
            SettingsEvent.DismissError -> chrome.update { it.copy(error = null) }
        }
    }

    private fun update(transform: (com.amteen.paisa.domain.model.AppSettings) -> com.amteen.paisa.domain.model.AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun runSampleData(block: suspend () -> Pair<String?, String?>) {
        if (chrome.value.sampleDataBusy) return
        viewModelScope.launch {
            chrome.update { it.copy(sampleDataBusy = true) }
            val (message, error) = block()
            chrome.update { it.copy(sampleDataBusy = false, message = message, error = error) }
        }
    }

    private data class Chrome(
        val sampleDataBusy: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )
}
