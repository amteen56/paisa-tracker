package com.amteen.paisa.domain.usecase

/**
 * How many records point at something the user is trying to remove.
 *
 * Deleting a referenced category or payment method would leave transactions holding
 * an id that no longer resolves, which is the one thing CLAUDE.md rule 4 forbids
 * outright. Counting is therefore a precondition of hard delete, not a nicety.
 *
 * Transactions are not the only referrer — a budget also names a category, and a
 * dangling budget is just as broken as a dangling transaction. Counting only
 * transactions is the easy version of this bug.
 */
data class ReferenceCount(
    val transactions: Int = 0,
    val budgets: Int = 0,
) {
    val total: Int get() = transactions + budgets

    val isReferenced: Boolean get() = total > 0

    /**
     * "3 transactions and 1 budget" — for the dialog that explains why Delete is
     * not on offer. Says what is in the way rather than just refusing.
     */
    fun describe(): String {
        val parts = buildList {
            if (transactions > 0) add(plural(transactions, "transaction"))
            if (budgets > 0) add(plural(budgets, "budget"))
        }
        return when (parts.size) {
            0 -> "nothing"
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }

    private fun plural(count: Int, noun: String): String =
        if (count == 1) "1 $noun" else "$count ${noun}s"
}

/**
 * The result of asking to remove a category, subcategory or payment method.
 *
 * [Blocked] is a success, not an error: the app looked, found references, and wrote
 * nothing. The UI turns it into the offer to archive instead.
 */
sealed interface RemovalOutcome {

    /** Reference count was zero, so the record is gone for good. */
    data object Deleted : RemovalOutcome

    /** Hidden from pickers, still resolvable by history and reports. */
    data object Archived : RemovalOutcome

    /** Still referenced — nothing was written. Offer [RemovalOutcome.Archived]. */
    data class Blocked(val references: ReferenceCount) : RemovalOutcome
}
