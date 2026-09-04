package com.amteen.paisa.ui.screen.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.time.PeriodFilter
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.BuildReportUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The reports screen.
 *
 * Holds the period, the drilled-into category and whether the range picker is up;
 * everything else comes from [BuildReportUseCase]. Changing the period re-resolves
 * the read, so a wide period costs its files only while it is selected.
 */
class ReportsViewModel(
    buildReport: BuildReportUseCase,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val request = MutableStateFlow(BuildReportUseCase.Request())
    private val showRangePicker = MutableStateFlow(false)

    /** Bumped by Retry, which re-subscribes the whole chain. */
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReportsUiState> = attempt
        .flatMapLatest {
            combine(buildReport(request), showRangePicker) { report, pickerOpen ->
                ReportsUiState(
                    isLoading = false,
                    period = report.period,
                    report = report,
                    showRangePicker = pickerOpen,
                )
            }.catch { failure ->
                emit(
                    ReportsUiState(
                        isLoading = false,
                        period = request.value.period,
                        showRangePicker = false,
                        error = failure.message ?: "Could not build this report.",
                    ),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(),
        )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()
        }
    }

    fun onEvent(event: ReportsEvent) {
        when (event) {
            // Changing period drops the drill-down: a subcategory breakdown for a
            // category that may not even appear in the new period is stale figures
            // under a heading that still looks current.
            is ReportsEvent.PeriodSelected -> request.update {
                it.copy(period = event.period, selectedCategoryId = null)
            }

            is ReportsEvent.CategoryToggled -> request.update {
                val next = if (it.selectedCategoryId == event.categoryId) null else event.categoryId
                it.copy(selectedCategoryId = next)
            }

            ReportsEvent.OpenRangePicker -> showRangePicker.value = true
            ReportsEvent.DismissRangePicker -> showRangePicker.value = false

            is ReportsEvent.CustomRangeSelected -> {
                showRangePicker.value = false
                request.update {
                    it.copy(
                        period = PeriodFilter.Custom(event.range),
                        selectedCategoryId = null,
                    )
                }
            }

            ReportsEvent.Retry -> attempt.update { it + 1 }
        }
    }
}
