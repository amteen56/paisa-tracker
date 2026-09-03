package com.amteen.paisa.ui.screen.home

import com.amteen.paisa.domain.usecase.DashboardSummary

/**
 * What the dashboard renders.
 *
 * Everything numeric lives on [summary], which a use case derived — the screen holds
 * no figures of its own and computes nothing. See CLAUDE.md.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummary? = null,
    val error: String? = null,
) {
    /** True once loading has finished and there is genuinely nothing to show. */
    val isEmpty: Boolean get() = !isLoading && error == null && summary?.hasAnyTransactions != true
}

sealed interface HomeEvent {
    /**
     * Re-subscribes the whole chain. The dashboard has no filters to reset and
     * nothing to dismiss, so this is the only thing it can be asked to do.
     */
    data object Retry : HomeEvent
}
