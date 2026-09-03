package com.amteen.paisa.core.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting for dates and times.
 *
 * ISO forms used for storage live in the mappers, not here — this file is purely
 * about what a human reads.
 */
object DateFormatters {

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun date(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> if (date.year == today.year) date.format(weekdayDayMonth)
        else date.format(dayMonthYear)
    }

    /** For sticky headers in the history list, which need a stable, dense label. */
    fun listHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(weekdayDayMonth)
        else date.format(dayMonthYear)
    }

    fun fullDate(date: LocalDate): String = date.format(dayMonthYear)

    fun compactDate(date: LocalDate): String = date.format(dayMonth)

    fun month(month: YearMonth): String = month.atDay(1).format(monthYear)

    fun monthShort(month: YearMonth): String = month.atDay(1).format(shortMonth)

    fun time(time: LocalTime): String = time.format(clock)
}
