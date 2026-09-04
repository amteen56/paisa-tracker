package com.amteen.paisa.ui.screen.reports

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.core.time.PeriodFilter
import com.amteen.paisa.domain.usecase.Report

/**
 * What the reports screen renders.
 *
 * Every figure lives on [report], which a use case derived. The screen holds only
 * the period, the drill-down and whether the range picker is open — see CLAUDE.md.
 */
data class ReportsUiState(
    val isLoading: Boolean = true,
    val period: PeriodFilter = PeriodFilter.ThisMonth,
    val report: Report? = null,
    val showRangePicker: Boolean = false,
    val error: String? = null,
) {
    /** Loading has finished and the chosen period has nothing in it. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && report?.hasAnyTransactions != true

    /** The category drilled into, if any — drives the subcategory section. */
    val drilledCategoryId: String? get() = report?.selectedCategoryId
}

sealed interface ReportsEvent {
    data class PeriodSelected(val period: PeriodFilter) : ReportsEvent

    /**
     * Tapping the category already open closes it, so the same control both opens
     * and closes the drill-down.
     */
    data class CategoryToggled(val categoryId: String) : ReportsEvent

    data object OpenRangePicker : ReportsEvent
    data object DismissRangePicker : ReportsEvent
    data class CustomRangeSelected(val range: DateRange) : ReportsEvent

    /** Re-subscribes the whole chain after a failure. */
    data object Retry : ReportsEvent
}
