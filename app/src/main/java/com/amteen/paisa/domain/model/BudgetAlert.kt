package com.amteen.paisa.domain.model

import java.time.YearMonth

/**
 * A record that one budget's alert for one threshold, in one month, has already
 * been shown.
 *
 * This exists so an alert fires **once per threshold per period**. Without it the
 * check would re-fire on every app start and after every saved transaction, and a
 * user 5% over their food budget would be told about it a dozen times a day.
 *
 * It is a record of *notification*, not of spending: nothing here is derived from
 * it, and deleting the file loses nothing but the memory of what has been announced.
 * Crossing back below a threshold does not clear the record — the alert has already
 * been shown, and re-announcing the same crossing after an edit is exactly the noise
 * this is here to prevent.
 */
data class BudgetAlert(
    val budgetId: String,
    val period: YearMonth,
    /** 75, 90 or 100 — see [BudgetAlertThresholds]. */
    val threshold: Int,
)

/**
 * The three points at which the user is told.
 *
 * Fixed by CLAUDE.md rule 7, and deliberately the same numbers that drive
 * [BudgetStatus] — the colour on screen and the notification in the shade must never
 * disagree about when a budget got serious.
 */
object BudgetAlertThresholds {
    const val WARNING = 75
    const val CRITICAL = 90
    const val EXCEEDED = 100

    /** Ascending, so "the highest one crossed" is the last match. */
    val all = listOf(WARNING, CRITICAL, EXCEEDED)
}
