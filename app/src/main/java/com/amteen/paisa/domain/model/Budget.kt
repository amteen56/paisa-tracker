package com.amteen.paisa.domain.model

import com.amteen.paisa.core.money.Money
import java.time.YearMonth

/**
 * A monthly spending limit for one category, or for one subcategory within it.
 *
 * [period] `null` means the budget recurs every month; a value pins it to that one
 * month. Usage is never stored — it is derived from transactions on every read, so
 * it cannot drift out of step with the underlying data. See CLAUDE.md rule 6.
 */
data class Budget(
    val id: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val limitMinor: Long,
    val currencyCode: String,
    val period: YearMonth? = null,
    /**
     * Position in the budgets list, lowest first.
     *
     * User-chosen, because the order people want is priority — rent before coffee —
     * and no derivable ordering expresses that. Same field and same reorder
     * mechanics as [Category] and [PaymentMethod].
     */
    val sortOrder: Int = 0,
    val archived: Boolean = false,
) {
    val limit: Money get() = Money(limitMinor, currencyCode)

    val isRecurring: Boolean get() = period == null

    fun appliesTo(month: YearMonth): Boolean = !archived && (period == null || period == month)
}

/**
 * How close a budget is to its limit. Thresholds are fixed by CLAUDE.md rule 7 and
 * are also what the budget notifications fire on.
 */
enum class BudgetStatus {
    NORMAL,
    WARNING,
    CRITICAL,
    EXCEEDED;

    companion object {
        fun forPercent(percent: Double): BudgetStatus = when {
            percent >= 100.0 -> EXCEEDED
            percent >= 90.0 -> CRITICAL
            percent >= 75.0 -> WARNING
            else -> NORMAL
        }
    }
}

/**
 * A budget together with its derived figures, all expressed in the **budget's own
 * currency** — expenses in other currencies are converted before summing, never
 * compared as raw numbers. See CLAUDE.md rule 7.
 *
 */
data class BudgetProgress(
    val budget: Budget,
    val month: YearMonth,
    val spentMinor: Long,
) {
    val spent: Money get() = Money(spentMinor, budget.currencyCode)

    val remainingMinor: Long get() = budget.limitMinor - spentMinor

    val remaining: Money get() = Money(remainingMinor, budget.currencyCode)

    val percent: Double
        get() = if (budget.limitMinor <= 0L) 0.0
        else spentMinor.toDouble() / budget.limitMinor.toDouble() * 100.0

    val status: BudgetStatus get() = BudgetStatus.forPercent(percent)

    /** Clamped for progress bars, which must not overflow their track. */
    val fraction: Float get() = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
}
