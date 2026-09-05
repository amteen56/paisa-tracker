package com.amteen.paisa.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Route builders, which are the one piece of navigation with logic in it.
 *
 * The dates matter more than they look. A day travels to a screen as text and comes
 * back through `LocalDate.parse`, so a route that formats it by hand — or that stops
 * matching the bare path once an optional argument is added — fails somewhere far
 * from here, as a screen that opens on the wrong day or not at all.
 */
class RoutesTest {

    // -- Calendar ------------------------------------------------------------

    @Test
    fun `calendar with no date is exactly the bare path`() {
        // The More menu navigates with the bare constant. If the builder ever grew a
        // trailing "?date=" the two would drift apart and that entry would dead-end.
        assertEquals(Routes.CALENDAR, Routes.calendar())
        assertEquals(Routes.CALENDAR, Routes.calendar(null))
    }

    @Test
    fun `the calendar pattern is a superset of the bare path`() {
        // What lets a plain "calendar" keep matching the registered
        // "calendar?date={date}" destination.
        assertTrue(Routes.CALENDAR_ROUTE.startsWith(Routes.CALENDAR))
        assertEquals("calendar?date={date}", Routes.CALENDAR_ROUTE)
    }

    @Test
    fun `calendar writes the date as ISO-8601 and it parses back`() {
        // Single digits, where a hand-rolled format would drop the padding.
        val date = LocalDate.of(2026, 1, 5)

        val route = Routes.calendar(date)

        assertEquals("calendar?date=2026-01-05", route)
        assertEquals(date, LocalDate.parse(route.substringAfter("date=")))
    }

    @Test
    fun `a calendar date in another year round-trips`() {
        val date = LocalDate.of(2019, 12, 31)

        assertEquals(date, LocalDate.parse(Routes.calendar(date).substringAfter("date=")))
    }

    // -- Add transaction -----------------------------------------------------
    // The pattern the calendar route copies. Untested until now.

    @Test
    fun `addTransaction omits the date when there is none`() {
        assertEquals("transaction/add/expense", Routes.addTransaction(TransactionTypeArg.EXPENSE))
    }

    @Test
    fun `addTransaction writes the date as ISO-8601 and it parses back`() {
        val date = LocalDate.of(2026, 3, 9)

        val route = Routes.addTransaction(TransactionTypeArg.INCOME, date)

        assertEquals("transaction/add/income?date=2026-03-09", route)
        assertEquals(date, LocalDate.parse(route.substringAfter("date=")))
    }
}
