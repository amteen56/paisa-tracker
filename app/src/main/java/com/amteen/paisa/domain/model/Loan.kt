package com.amteen.paisa.domain.model

import com.amteen.paisa.core.money.Money
import java.time.LocalDate

/** Which way the money went. */
enum class LoanDirection {
    /** The user handed money over and expects it back. */
    LENT,

    /** The user received money and owes it. */
    BORROWED,
    ;

    val isLent: Boolean get() = this == LENT
}

/**
 * One instalment against a loan.
 *
 * Partial repayments are the normal case, not the exception — people pay back what they
 * have when they have it — so a loan holds a list of these rather than a single
 * "returned on" date.
 *
 * [paymentMethodId] records how the money actually moved. It is nullable because a
 * method the user later deleted must not stop the repayment rendering.
 */
data class Repayment(
    val id: String,
    val amountMinor: Long,
    val date: LocalDate,
    val paymentMethodId: String? = null,
    val note: String = "",
) {
    val amount: Money get() = Money(amountMinor, CURRENCY)

    companion object {
        /** Everything in Paisa is PKR. Held here so [amount] has a code to carry. */
        const val CURRENCY = "PKR"
    }
}

/**
 * Money lent to, or borrowed from, a person.
 *
 * A standalone ledger. A loan is **not** a transaction: handing a friend Rs. 5,000 is
 * not spending it, and recording it as an expense would understate the user's net worth
 * by the amount they are owed, distort every category breakdown, and eat into a budget
 * for a category the money never belonged to. So loans never touch totals, budgets or
 * reports — they answer one question the ledger cannot: who still owes what.
 *
 * Settlement is **derived** from [repayments], never stored, for the same reason totals
 * are (CLAUDE.md rule 6): a stored "settled" flag and a repayment list can disagree,
 * and then there is no telling which one is the truth.
 */
data class Loan(
    val id: String,
    /** The person's name, as the user typed it. There is no contacts integration. */
    val counterparty: String,
    val direction: LoanDirection,
    /** What was originally handed over. Never rewritten by a repayment. */
    val principalMinor: Long,
    val currencyCode: String,
    /** When the money changed hands. */
    val date: LocalDate,
    /** Optional. Only meaningful for chasing something up, never enforced. */
    val dueDate: LocalDate? = null,
    val note: String = "",
    val repayments: List<Repayment> = emptyList(),
    val sortOrder: Int = 0,
) {
    val repaidMinor: Long get() = repayments.sumOf { it.amountMinor }

    /**
     * What is still owed, floored at zero.
     *
     * The floor is belt and braces: `RecordRepaymentUseCase` refuses a repayment larger
     * than the outstanding amount, so an over-repaid loan can only come from a
     * hand-edited file — and a negative "outstanding" would render as a nonsense
     * negative balance on the dashboard.
     */
    val outstandingMinor: Long get() = (principalMinor - repaidMinor).coerceAtLeast(0L)

    val isSettled: Boolean get() = repaidMinor >= principalMinor

    val principal: Money get() = Money(principalMinor, currencyCode)
    val repaid: Money get() = Money(repaidMinor, currencyCode)
    val outstanding: Money get() = Money(outstandingMinor, currencyCode)

    /**
     * How much of the loan has come back, 0f..1f. Display only — never money.
     *
     * A zero principal cannot happen through the app (`SaveLoanUseCase` requires a
     * positive amount) but is guarded anyway, because a divide-by-zero here would take
     * down the whole list for one bad record.
     */
    val repaidFraction: Float
        get() = if (principalMinor <= 0L) {
            0f
        } else {
            (repaidMinor.toDouble() / principalMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    /** Past its due date with money still owed. */
    fun isOverdue(today: LocalDate): Boolean =
        !isSettled && dueDate != null && dueDate < today

    fun repayment(id: String): Repayment? = repayments.firstOrNull { it.id == id }
}
