package com.amteen.paisa.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.GetDashboardSummaryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The dashboard.
 *
 * Holds no figures and does no arithmetic: it subscribes to
 * [GetDashboardSummaryUseCase] and forwards what comes back. The only state it owns
 * is the retry counter and a dismissed error.
 */
class HomeViewModel(
    getDashboardSummary: GetDashboardSummaryUseCase,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    /** Bumped by Retry, which re-subscribes the whole chain. */
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = attempt
        .flatMapLatest {
            getDashboardSummary()
                .map { summary -> HomeUiState(isLoading = false, summary = summary) }
                // A rate the user typed as 0 makes conversion impossible, and the
                // dashboard is the first screen that would hit it. Reads recover
                // rather than crashing — see CLAUDE.md rule 2 — so it surfaces as a
                // message with a way back, not a stack trace.
                .catch { failure ->
                    emit(
                        HomeUiState(
                            isLoading = false,
                            error = failure.message
                                ?: "Could not work out your totals.",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()
            budgetRepository.load()
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Retry -> attempt.update { it + 1 }
        }
    }
}
