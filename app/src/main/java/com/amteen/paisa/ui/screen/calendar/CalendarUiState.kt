package com.amteen.paisa.ui.screen.calendar

import com.amteen.paisa.domain.usecase.CalendarDay
import com.amteen.paisa.domain.usecase.MonthCalendar
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the calendar renders.
 *
 * Every figure lives on [calendar], which a use case derived. The screen holds only
 * the month being viewed and which day is open — see CLAUDE.md.
 */
data class CalendarUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val calendar: MonthCalendar? = null,
    val selectedDate: LocalDate? = null,
    val error: String? = null,
) {
    /** Loading has finished and this month has nothing recorded in it. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && calendar?.hasAnyTransactions != true

    /** The open day sheet's content, resolved out of the grid already in memory. */
    val selectedDay: CalendarDay?
        get() = selectedDate?.let { calendar?.day(it) }
}

sealed interface CalendarEvent {
    data object PreviousMonth : CalendarEvent
    data object NextMonth : CalendarEvent
    data object ThisMonth : CalendarEvent

    /**
     * A day cell was tapped. May be a day from a neighbouring month, in which case
     * the grid follows it — see [CalendarViewModel.onEvent].
     */
    data class DaySelected(val date: LocalDate) : CalendarEvent

    data object DayDismissed : CalendarEvent

    /** Re-subscribes the whole chain after a conversion failure. */
    data object Retry : CalendarEvent
}
