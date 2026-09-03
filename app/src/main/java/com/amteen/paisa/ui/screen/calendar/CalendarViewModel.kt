package com.amteen.paisa.ui.screen.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.GetMonthCalendarUseCase
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * The calendar.
 *
 * The month and the open day are the only state here; every figure is derived from
 * the month and the ledger by [GetMonthCalendarUseCase]. Stepping months reads only
 * the shards that step's grid actually spans, so paging back through years costs a
 * file or two per step rather than the whole ledger — the same shape the budgets
 * screen uses.
 */
class CalendarViewModel(
    getMonthCalendar: GetMonthCalendarUseCase,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    /** Bumped by Retry, which re-subscribes the whole chain. */
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CalendarUiState> = attempt
        .flatMapLatest {
            combine(getMonthCalendar(month), selectedDate) { calendar, selected ->
                CalendarUiState(
                    isLoading = false,
                    month = calendar.month,
                    calendar = calendar,
                    // A day the grid no longer shows cannot stay open. Stepping the
                    // month with a sheet up would otherwise leave it describing a day
                    // that is not on the screen behind it.
                    selectedDate = selected?.takeIf { date -> calendar.day(date) != null },
                )
            }.catch { failure ->
                // A rate the user typed as 0 makes conversion impossible. Reads
                // recover rather than crashing — CLAUDE.md rule 2 — so it surfaces
                // as a message with a way back, not a stack trace.
                emit(
                    CalendarUiState(
                        isLoading = false,
                        month = month.value,
                        error = failure.message ?: "Could not work out this month.",
                    ),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(),
        )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()
        }
    }

    fun onEvent(event: CalendarEvent) {
        when (event) {
            CalendarEvent.PreviousMonth -> month.update { it.minusMonths(1) }
            CalendarEvent.NextMonth -> month.update { it.plusMonths(1) }
            CalendarEvent.ThisMonth -> month.value = YearMonth.now()

            // Tapping a leading or trailing day moves the grid to the month that day
            // belongs to. Opening a sheet for the 1st of next month while the header
            // still said September would leave the user unsure which month they were
            // looking at once they dismissed it.
            is CalendarEvent.DaySelected -> {
                month.value = YearMonth.from(event.date)
                selectedDate.value = event.date
            }

            CalendarEvent.DayDismissed -> selectedDate.value = null
            CalendarEvent.Retry -> attempt.update { it + 1 }
        }
    }
}
