package com.amteen.paisa.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The period selector shared by history, reports and the dashboard.
 *
 * [resolve] takes `today` as a parameter rather than reading the clock, so every
 * period is deterministic and testable — and so a screen left open overnight
 * recomputes against the real date when it next resolves.
 */
sealed interface PeriodFilter {

    data object Today : PeriodFilter
    data object Yesterday : PeriodFilter
    data object ThisWeek : PeriodFilter
    data object ThisMonth : PeriodFilter
    data object LastMonth : PeriodFilter
    data object ThisYear : PeriodFilter
    data object AllTime : PeriodFilter
    data class Month(val month: YearMonth) : PeriodFilter
    data class Custom(val range: DateRange) : PeriodFilter

    /** `null` for [AllTime] — the caller loads every shard rather than a span. */
    fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): DateRange? =
        when (this) {
            Today -> DateRange.singleDay(today)
            Yesterday -> DateRange.singleDay(today.minusDays(1))
            ThisWeek -> {
                val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                DateRange(start, start.plusDays(6))
            }
            ThisMonth -> DateRange.of(YearMonth.from(today))
            LastMonth -> DateRange.of(YearMonth.from(today).minusMonths(1))
            ThisYear -> DateRange(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))
            is Month -> DateRange.of(month)
            is Custom -> range
            AllTime -> null
        }
}
