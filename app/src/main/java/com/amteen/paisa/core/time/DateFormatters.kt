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
 *
 * **Formatters are cached per locale, not per process.** The first version held six
 * `DateTimeFormatter`s built once from `Locale.getDefault()`, which meant that if the
 * user changed their device language the whole app carried on formatting dates in the
 * old one until the process was killed. Caching on the current locale keeps the
 * allocation saving without the staleness.
 */
object DateFormatters {

    private class Formatters(locale: Locale) {
        val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val weekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
        val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val shortMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", locale)
        val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
    }

    @Volatile
    private var cached: Pair<Locale, Formatters>? = null

    /**
     * The formatters for the current locale, rebuilt only when it actually changes.
     *
     * A benign race here costs one redundant set of formatters, never a wrong string,
     * so it needs no lock — and these are called on every row of a scrolling list.
     */
    private fun current(): Formatters {
        val locale = Locale.getDefault()
        cached?.let { (cachedLocale, formatters) ->
            if (cachedLocale == locale) return formatters
        }
        return Formatters(locale).also { cached = locale to it }
    }

    fun date(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> if (date.year == today.year) date.format(current().weekdayDayMonth)
        else date.format(current().dayMonthYear)
    }

    /** For sticky headers in the history list, which need a stable, dense label. */
    fun listHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(current().weekdayDayMonth)
        else date.format(current().dayMonthYear)
    }

    fun fullDate(date: LocalDate): String = date.format(current().dayMonthYear)

    fun compactDate(date: LocalDate): String = date.format(current().dayMonth)

    fun month(month: YearMonth): String = month.atDay(1).format(current().monthYear)

    fun monthShort(month: YearMonth): String = month.atDay(1).format(current().shortMonth)

    fun time(time: LocalTime): String = time.format(current().clock)
}
