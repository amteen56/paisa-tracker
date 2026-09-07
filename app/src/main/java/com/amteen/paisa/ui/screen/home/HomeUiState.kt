package com.amteen.paisa.ui.screen.home

import com.amteen.paisa.domain.model.AverageFilterMode
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.usecase.DashboardSummary
import com.amteen.paisa.domain.usecase.LoanSummary

/**
 * What the dashboard renders.
 *
 * Everything numeric lives on [summary], which a use case derived — the screen holds
 * no figures of its own and computes nothing. See CLAUDE.md.
 *
 * The `average*` fields are the daily-average filter dialog: which categories it can
 * offer and what the user has already chosen. They are not figures, and the average
 * itself still arrives on [summary] already filtered.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummary? = null,
    val error: String? = null,
    val averageFilterVisible: Boolean = false,
    /** Main, non-archived, expense-capable categories — the dialog's whole list. */
    val averageCategories: List<Category> = emptyList(),
    val averageFilterMode: AverageFilterMode = AverageFilterMode.EXCLUDE,
    val averageFilterCategoryIds: Set<String> = emptySet(),

    /**
     * What is still owed, either way.
     *
     * A separate object from [summary] because loans are a separate ledger: they
     * are not spending, and folding them into the dashboard summary would invite
     * exactly the arithmetic that keeps them out of the totals.
     */
    val loans: LoanSummary? = null,
) {
    /** True once loading has finished and there is genuinely nothing to show. */
    val isEmpty: Boolean get() = !isLoading && error == null && summary?.hasAnyTransactions != true
}

sealed interface HomeEvent {
    /** Re-subscribes the whole chain. */
    data object Retry : HomeEvent

    /** Opens the daily-average filter. */
    data object DailyAverageClicked : HomeEvent

    data object AverageFilterDismissed : HomeEvent

    data class AverageFilterModeChanged(val mode: AverageFilterMode) : HomeEvent

    data class AverageFilterCategoryToggled(val categoryId: String) : HomeEvent

    /** Back to the unfiltered average, without having to untick each category. */
    data object AverageFilterCleared : HomeEvent
}
