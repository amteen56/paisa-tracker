package com.amteen.paisa.domain.model

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.time.PeriodFilter

/**
 * A transaction with its references already resolved.
 *
 * The list and detail screens need the category name, colour, icon, payment method
 * and currency together with the record. Resolving them in a use case — once per
 * emission, against maps — keeps that work out of the composables, which must not
 * be doing lookups during a scroll. See CLAUDE.md.
 *
 * [category] is nullable because a transaction may outlive a category that was hard
 * deleted while unreferenced elsewhere; the UI renders "Uncategorised" rather than
 * dropping the row.
 */
data class TransactionDetails(
    val transaction: Transaction,
    val category: Category?,
    val subcategory: Subcategory?,
    val paymentMethod: PaymentMethod?,
    val currency: Currency,
) {
    val id: String get() = transaction.id
    val money: Money get() = transaction.money
    val isExpense: Boolean get() = transaction.type.isExpense

    /** What the row shows as its title: the user's own words, else the category. */
    val title: String
        get() = transaction.description.ifBlank { category?.name ?: "Uncategorised" }

    /** The supporting line: category, narrowed by subcategory when there is one. */
    val subtitle: String
        get() {
            val categoryName = category?.name ?: "Uncategorised"
            return subcategory?.let { "$categoryName · ${it.name}" } ?: categoryName
        }
}

/** Sort options offered by the history screen. */
enum class TransactionSort {
    NEWEST_FIRST,
    OLDEST_FIRST,
    AMOUNT_HIGH_FIRST,
    AMOUNT_LOW_FIRST;

    val label: String
        get() = when (this) {
            NEWEST_FIRST -> "Newest first"
            OLDEST_FIRST -> "Oldest first"
            AMOUNT_HIGH_FIRST -> "Amount: high to low"
            AMOUNT_LOW_FIRST -> "Amount: low to high"
        }

    companion object {
        fun from(order: SortOrder): TransactionSort = when (order) {
            SortOrder.DATE_DESC -> NEWEST_FIRST
            SortOrder.DATE_ASC -> OLDEST_FIRST
            SortOrder.AMOUNT_DESC -> AMOUNT_HIGH_FIRST
            SortOrder.AMOUNT_ASC -> AMOUNT_LOW_FIRST
        }
    }
}

/**
 * Everything the history screen filters by.
 *
 * An empty set means "no restriction" rather than "match nothing" — that way the
 * default query is the zero value and a cleared filter chip needs no special case.
 */
data class TransactionQuery(
    val text: String = "",
    val types: Set<TransactionType> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val subcategoryIds: Set<String> = emptySet(),
    val paymentMethodIds: Set<String> = emptySet(),
    /** Compared against the amount converted to base, so a mixed list filters sanely. */
    val minAmountMinorBase: Long? = null,
    val maxAmountMinorBase: Long? = null,
    val period: PeriodFilter = PeriodFilter.ThisMonth,
    val sort: TransactionSort = TransactionSort.NEWEST_FIRST,
) {
    /** Drives the "Clear filters" affordance and the filter-count badge. */
    val activeFilterCount: Int
        get() = listOf(
            types.isNotEmpty(),
            categoryIds.isNotEmpty(),
            subcategoryIds.isNotEmpty(),
            paymentMethodIds.isNotEmpty(),
            minAmountMinorBase != null,
            maxAmountMinorBase != null,
        ).count { it }

    val hasQuery: Boolean get() = text.isNotBlank() || activeFilterCount > 0
}

/**
 * Income, expense and net for a set of transactions, converted into one currency.
 *
 * [mixedCurrency] means at least one amount was converted using a manual rate, which
 * the UI must say out loud rather than presenting the figure as exact.
 */
data class TransactionTotals(
    val income: Money,
    val expense: Money,
    val mixedCurrency: Boolean,
    val count: Int,
) {
    val net: Money get() = income - expense

    companion object {
        fun empty(currencyCode: String) = TransactionTotals(
            income = Money.zero(currencyCode),
            expense = Money.zero(currencyCode),
            mixedCurrency = false,
            count = 0,
        )
    }
}
