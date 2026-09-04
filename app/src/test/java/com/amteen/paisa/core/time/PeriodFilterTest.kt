package com.amteen.paisa.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Period resolution.
 *
 * Shared by history, the dashboard, the calendar and reports, so an off-by-one here
 * is an off-by-one in every figure the app shows. `resolve` takes `today` as a
 * parameter, which is what makes each of these deterministic.
 */
class PeriodFilterTest {

    /** A Tuesday. */
    private val today = LocalDate.of(2026, 9, 15)

    @Test
    fun `today and yesterday are single days`() {
        assertEquals(DateRange(today, today), PeriodFilter.Today.resolve(today))
        assertEquals(
            DateRange(today.minusDays(1), today.minusDays(1)),
            PeriodFilter.Yesterday.resolve(today),
        )
        assertEquals(1, PeriodFilter.Today.resolve(today)!!.dayCount)
    }

    @Test
    fun `this week honours the user's first day of week`() {
        val monday = PeriodFilter.ThisWeek.resolve(today, DayOfWeek.MONDAY)!!
        assertEquals(LocalDate.of(2026, 9, 14), monday.start)
        assertEquals(LocalDate.of(2026, 9, 20), monday.endInclusive)

        val sunday = PeriodFilter.ThisWeek.resolve(today, DayOfWeek.SUNDAY)!!
        assertEquals(LocalDate.of(2026, 9, 13), sunday.start)

        val saturday = PeriodFilter.ThisWeek.resolve(today, DayOfWeek.SATURDAY)!!
        assertEquals(LocalDate.of(2026, 9, 12), saturday.start)

        // Always seven days, wherever the week starts.
        assertEquals(7, monday.dayCount)
        assertEquals(7, sunday.dayCount)
        assertEquals(7, saturday.dayCount)
    }

    @Test
    fun `this week includes today whatever the first day is`() {
        for (firstDay in DayOfWeek.entries) {
            val range = PeriodFilter.ThisWeek.resolve(today, firstDay)!!
            assertTrue("$firstDay should contain today", today in range)
        }
    }

    @Test
    fun `a week starting on today's own weekday starts today`() {
        // Today is a Tuesday, so a Tuesday-start week begins now rather than a week
        // ago — `previousOrSame`, not `previous`.
        val range = PeriodFilter.ThisWeek.resolve(today, DayOfWeek.TUESDAY)!!
        assertEquals(today, range.start)
    }

    @Test
    fun `this month and last month cover whole months`() {
        val thisMonth = PeriodFilter.ThisMonth.resolve(today)!!
        assertEquals(LocalDate.of(2026, 9, 1), thisMonth.start)
        assertEquals(LocalDate.of(2026, 9, 30), thisMonth.endInclusive)

        val lastMonth = PeriodFilter.LastMonth.resolve(today)!!
        assertEquals(LocalDate.of(2026, 8, 1), lastMonth.start)
        assertEquals(LocalDate.of(2026, 8, 31), lastMonth.endInclusive)
    }

    @Test
    fun `last month from the 31st does not overflow a short month`() {
        // 31 March back to February is the classic date-arithmetic trap.
        val range = PeriodFilter.LastMonth.resolve(LocalDate.of(2026, 3, 31))!!
        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
    }

    @Test
    fun `this year covers the whole year, leap year included`() {
        val ordinary = PeriodFilter.ThisYear.resolve(today)!!
        assertEquals(LocalDate.of(2026, 1, 1), ordinary.start)
        assertEquals(LocalDate.of(2026, 12, 31), ordinary.endInclusive)
        assertEquals(365, ordinary.dayCount)

        val leap = PeriodFilter.ThisYear.resolve(LocalDate.of(2028, 6, 1))!!
        assertEquals(366, leap.dayCount)
    }

    @Test
    fun `all time resolves to nothing, so the caller reads every shard`() {
        // Null is the signal to load everything rather than a span; a huge range
        // would be a lie about what the user asked for.
        assertNull(PeriodFilter.AllTime.resolve(today))
    }

    @Test
    fun `an explicit month and a custom range are passed through`() {
        val month = PeriodFilter.Month(YearMonth.of(2025, 2)).resolve(today)!!
        assertEquals(LocalDate.of(2025, 2, 1), month.start)
        assertEquals(LocalDate.of(2025, 2, 28), month.endInclusive)

        val custom = DateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9))
        assertEquals(custom, PeriodFilter.Custom(custom).resolve(today))
    }

    @Test
    fun `every period has a label`() {
        val periods = listOf(
            PeriodFilter.Today,
            PeriodFilter.Yesterday,
            PeriodFilter.ThisWeek,
            PeriodFilter.ThisMonth,
            PeriodFilter.LastMonth,
            PeriodFilter.ThisYear,
            PeriodFilter.AllTime,
            PeriodFilter.Month(YearMonth.of(2026, 9)),
            PeriodFilter.Custom(DateRange(today, today.plusDays(3))),
        )

        // The label lives on the filter so history and reports cannot end up naming
        // the same period two different things.
        assertTrue(periods.all { it.label.isNotBlank() })
        assertEquals("This month", PeriodFilter.ThisMonth.label)
        assertEquals("All time", PeriodFilter.AllTime.label)
    }

    @Test
    fun `a custom label names both ends`() {
        val label = PeriodFilter.Custom(
            DateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 9)),
        ).label

        assertTrue(label, label.contains("5"))
        assertTrue(label, label.contains("9"))
    }
}
