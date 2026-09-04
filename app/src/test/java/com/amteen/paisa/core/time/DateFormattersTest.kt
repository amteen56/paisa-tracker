package com.amteen.paisa.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The relative date labels.
 *
 * Every one of these takes `today` as a parameter rather than reading the clock, which
 * is the only reason they are testable at all — and the reason a screen left open
 * overnight relabels itself instead of insisting yesterday is still Today.
 */
class DateFormattersTest {

    private val today = LocalDate.of(2026, 9, 15)

    @Test
    fun `today, yesterday and tomorrow are named rather than dated`() {
        assertEquals("Today", DateFormatters.date(today, today))
        assertEquals("Yesterday", DateFormatters.date(today.minusDays(1), today))
        assertEquals("Tomorrow", DateFormatters.date(today.plusDays(1), today))
    }

    @Test
    fun `the list header names today and yesterday but not tomorrow`() {
        assertEquals("Today", DateFormatters.listHeader(today, today))
        assertEquals("Yesterday", DateFormatters.listHeader(today.minusDays(1), today))

        // A sticky header saying "Tomorrow" above a future-dated entry reads like a
        // bug; the date itself is clearer.
        val tomorrow = DateFormatters.listHeader(today.plusDays(1), today)
        assertTrue(tomorrow.contains("16"))
    }

    @Test
    fun `a date in this year omits the year`() {
        val label = DateFormatters.date(LocalDate.of(2026, 3, 2), today)
        assertTrue(label.contains("2"))
        assertTrue("year should be omitted for this year: $label", !label.contains("2026"))
    }

    @Test
    fun `a date in another year includes the year`() {
        val label = DateFormatters.date(LocalDate.of(2025, 3, 2), today)
        // Without the year, an entry from last March is indistinguishable from this
        // March in a list that spans both.
        assertTrue("year should be shown: $label", label.contains("2025"))
    }

    @Test
    fun `the year boundary is handled on both sides`() {
        val lastDayOfLastYear = LocalDate.of(2025, 12, 31)
        val firstDayOfThisYear = LocalDate.of(2026, 1, 1)

        assertTrue(DateFormatters.date(lastDayOfLastYear, today).contains("2025"))
        assertTrue(!DateFormatters.date(firstDayOfThisYear, today).contains("2026"))
    }

    @Test
    fun `a full date always carries the year`() {
        // Used where there is no surrounding context to infer it from, such as the
        // day sheet and the import preview.
        assertTrue(DateFormatters.fullDate(today).contains("2026"))
        assertTrue(DateFormatters.fullDate(LocalDate.of(2025, 1, 1)).contains("2025"))
    }

    @Test
    fun `a compact date carries no year`() {
        // It labels chart axes, where the period is already stated above.
        assertTrue(!DateFormatters.compactDate(today).contains("2026"))
    }

    @Test
    fun `a month renders in full and in short form`() {
        val month = YearMonth.of(2026, 9)
        val full = DateFormatters.month(month)
        val short = DateFormatters.monthShort(month)

        assertTrue("full month should carry the year: $full", full.contains("2026"))
        assertTrue(short.length <= full.length)
        // The short form labels a trend axis, so it must not carry the year.
        assertTrue(!short.contains("2026"))
    }

    @Test
    fun `relative labels do not leak across a month boundary`() {
        val firstOfMonth = LocalDate.of(2026, 9, 1)
        val lastOfPrevious = LocalDate.of(2026, 8, 31)

        assertEquals("Today", DateFormatters.date(firstOfMonth, firstOfMonth))
        assertEquals("Yesterday", DateFormatters.date(lastOfPrevious, firstOfMonth))
    }
}
