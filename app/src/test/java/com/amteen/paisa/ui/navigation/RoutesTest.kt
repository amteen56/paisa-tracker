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

    // -- Loans ---------------------------------------------------------------

    @Test
    fun `loanEdit with no id is exactly the bare path`() {
        // The FAB navigates with the no-argument builder, and the destination is
        // registered as the optional-argument pattern. A trailing "?id=" here would
        // stop the two matching and Add Loan would dead-end.
        assertEquals("loans/edit", Routes.loanEdit())
        assertEquals("loans/edit", Routes.loanEdit(null))
    }

    @Test
    fun `loanEdit carries the id`() {
        assertEquals("loans/edit?id=l1", Routes.loanEdit("l1"))
    }

    @Test
    fun `the loan edit pattern is a superset of the bare path`() {
        assertTrue(Routes.LOAN_EDIT_ROUTE.startsWith("loans/edit"))
        assertEquals("loans/edit?id={id}", Routes.LOAN_EDIT_ROUTE)
    }

    @Test
    fun `the loans list route is not shadowed by the edit route`() {
        // "loans" and "loans/edit" are distinct destinations; if the list route ever
        // became a prefix match for the editor, tapping a loan would reopen the list.
        assertEquals("loans", Routes.LOANS)
        assertTrue(Routes.LOAN_EDIT_ROUTE != Routes.LOANS)
    }
}
