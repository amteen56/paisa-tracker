package com.amteen.paisa.core.time

import java.time.LocalDate
import java.time.YearMonth

/**
 * An inclusive span of calendar days.
 *
 * Everything date-related in the app is `LocalDate` — no instants, no zones. A
 * transaction happens on the day the user says it did, and must not shift because
 * they travelled or the device changed timezone.
 */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!start.isAfter(endInclusive)) { "Range starts ($start) after it ends ($endInclusive)." }
    }

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    val dayCount: Int get() = (endInclusive.toEpochDay() - start.toEpochDay()).toInt() + 1

    /** Every shard this range touches — what the repository has to load. */
    fun months(): List<YearMonth> {
        val first = YearMonth.from(start)
        val last = YearMonth.from(endInclusive)
        val result = ArrayList<YearMonth>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            result += cursor
            cursor = cursor.plusMonths(1)
        }
        return result
    }

    companion object {
        fun of(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())

        fun singleDay(date: LocalDate) = DateRange(date, date)
    }
}
