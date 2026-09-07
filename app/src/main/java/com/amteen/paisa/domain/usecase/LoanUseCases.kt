package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.model.Repayment
import com.amteen.paisa.domain.repository.LoanRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** What the loan form produces. [id] null means insert. */
data class LoanInput(
    val id: String? = null,
    val counterparty: String,
    val direction: LoanDirection,
    val principalMinor: Long,
    val currencyCode: String = Repayment.CURRENCY,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val note: String = "",
)

/**
 * Validates and persists a loan.
 *
 * Editing keeps the repayments that are already recorded, so the only interesting
 * validation is the principal: dropping it below what has already come back would make
 * a loan that is more than settled, and silently clamping the figure instead would
 * throw away the number the user actually typed. Refusing tells them what is wrong.
 */
class SaveLoanUseCase(
    private val loans: LoanRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend operator fun invoke(input: LoanInput): AppResult<Loan> {
        val existing = try {
            input.id?.let { loans.getById(it) }
        } catch (e: Exception) {
            return AppResult.Err(AppError.Storage("Could not read the loan.", e))
        }

        validate(input, existing)?.let { return AppResult.Err(it) }

        val loan = Loan(
            id = existing?.id ?: input.id ?: newId(),
            counterparty = input.counterparty.trim(),
            direction = input.direction,
            principalMinor = input.principalMinor,
            currencyCode = input.currencyCode.ifBlank { Repayment.CURRENCY },
            date = input.date,
            dueDate = input.dueDate,
            note = input.note.trim(),
            // Repayments are not the form's to rewrite. They are recorded one at a time
            // through RecordRepaymentUseCase, and editing the loan's amount or date
            // must not disturb the history of what came back.
            repayments = existing?.repayments ?: emptyList(),
            sortOrder = existing?.sortOrder ?: 0,
        )

        return try {
            loans.upsert(loan)
            AppResult.Ok(loan)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage(
                    "Could not save the loan. Your existing data is unchanged.",
                    e,
                ),
            )
        }
    }

    private fun validate(input: LoanInput, existing: Loan?): AppError.Validation? {
        val name = input.counterparty.trim()
        if (name.isEmpty()) {
            return AppError.Validation(FIELD_COUNTERPARTY, "Who is this loan with?")
        }
        if (name.length > MAX_NAME) {
            return AppError.Validation(
                FIELD_COUNTERPARTY,
                "Keep the name under $MAX_NAME characters.",
            )
        }
        if (input.principalMinor <= 0L) {
            return AppError.Validation(FIELD_AMOUNT, "Enter an amount greater than zero.")
        }
        if (input.dueDate != null && input.dueDate < input.date) {
            return AppError.Validation(
                FIELD_DUE_DATE,
                "The due date cannot be before the loan was made.",
            )
        }

        val repaid = existing?.repaidMinor ?: 0L
        if (input.principalMinor < repaid) {
            return AppError.Validation(
                FIELD_AMOUNT,
                "You have already recorded more coming back than that. " +
                    "Remove a repayment first if the amount was wrong.",
            )
        }

        return null
    }

    companion object {
        const val FIELD_COUNTERPARTY = "counterparty"
        const val FIELD_AMOUNT = "amount"
        const val FIELD_DUE_DATE = "dueDate"
        const val MAX_NAME = 60
    }
}

/**
 * Records money coming back against a loan.
 *
 * Partial repayments are the normal case, so this appends rather than closing the loan.
 * Settlement is derived: once the repayments add up to the principal the loan reports
 * itself settled, and there is nothing to mark.
 *
 * A repayment larger than what is outstanding is refused rather than clamped. Someone
 * typing 50,000 for a 5,000 loan has made a typo, and quietly accepting 5,000 of it
 * hides the mistake in a record they will later trust.
 */
class RecordRepaymentUseCase(
    private val loans: LoanRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend operator fun invoke(
        loanId: String,
        amountMinor: Long,
        date: LocalDate,
        paymentMethodId: String? = null,
        note: String = "",
    ): AppResult<Loan> {
        val loan = try {
            loans.getById(loanId)
        } catch (e: Exception) {
            return AppResult.Err(AppError.Storage("Could not read the loan.", e))
        } ?: return AppResult.Err(AppError.NotFound("That loan"))

        if (amountMinor <= 0L) {
            return AppResult.Err(
                AppError.Validation(FIELD_AMOUNT, "Enter an amount greater than zero."),
            )
        }
        if (loan.isSettled) {
            return AppResult.Err(
                AppError.Validation(FIELD_AMOUNT, "This loan is already settled."),
            )
        }
        if (amountMinor > loan.outstandingMinor) {
            // No amount in the message: formatting money is MoneyFormatter's job alone
            // (CLAUDE.md), and the form already has the outstanding figure on screen.
            return AppResult.Err(
                AppError.Validation(
                    FIELD_AMOUNT,
                    "That is more than is still owed on this loan.",
                ),
            )
        }
        if (paymentMethodId != null && paymentMethods.getById(paymentMethodId) == null) {
            return AppResult.Err(
                AppError.Validation(FIELD_PAYMENT_METHOD, "That payment method no longer exists."),
            )
        }

        val updated = loan.copy(
            repayments = loan.repayments + Repayment(
                id = newId(),
                amountMinor = amountMinor,
                date = date,
                paymentMethodId = paymentMethodId,
                note = note.trim(),
            ),
        )

        return try {
            loans.upsert(updated)
            AppResult.Ok(updated)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage("Could not save the repayment. The loan is unchanged.", e),
            )
        }
    }

    companion object {
        const val FIELD_AMOUNT = "amount"
        const val FIELD_PAYMENT_METHOD = "paymentMethod"
    }
}

/**
 * Removes one repayment, putting the amount back outstanding.
 *
 * The way out of a mistyped repayment. Without it the only fix for a wrong figure would
 * be to delete the loan and re-enter the whole history.
 */
class DeleteRepaymentUseCase(private val loans: LoanRepository) {
    suspend operator fun invoke(loanId: String, repaymentId: String): AppResult<Loan> {
        val loan = try {
            loans.getById(loanId)
        } catch (e: Exception) {
            return AppResult.Err(AppError.Storage("Could not read the loan.", e))
        } ?: return AppResult.Err(AppError.NotFound("That loan"))

        if (loan.repayment(repaymentId) == null) {
            return AppResult.Err(AppError.NotFound("That repayment"))
        }

        val updated = loan.copy(repayments = loan.repayments.filterNot { it.id == repaymentId })

        return try {
            loans.upsert(updated)
            AppResult.Ok(updated)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage("Could not remove the repayment. The loan is unchanged.", e),
            )
        }
    }
}

/**
 * Deletes a loan and its repayment history.
 *
 * No reference check: nothing in the app points at a loan. See `FileLoanRepositoryImpl`.
 */
class DeleteLoanUseCase(private val loans: LoanRepository) {
    suspend operator fun invoke(loanId: String): AppResult<Unit> = try {
        loans.hardDelete(loanId)
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not delete the loan. Nothing was changed.", e))
    }
}

/** One person's outstanding balance, for the dashboard chart. */
data class LoanSlice(
    val counterparty: String,
    val direction: LoanDirection,
    val amountMinor: Long,
    val currencyCode: String,
    /** 0f..1f of the largest slice. Display only — never money. */
    val share: Float,
) {
    val amount: Money get() = Money(amountMinor, currencyCode)
}

/**
 * What is still owed, in both directions.
 *
 * Derived from the loans on every read, like every other figure in the app (CLAUDE.md
 * rule 6). Feeds both the dashboard card and the Loans screen header, so the two can
 * never disagree about what is outstanding.
 */
data class LoanSummary(
    val outstandingLentMinor: Long,
    val outstandingBorrowedMinor: Long,
    val currencyCode: String,
    /** Loans with anything still owed, either way. */
    val openCount: Int,
    val overdueCount: Int,
    /** Biggest balances first, capped at [MAX_SLICES]. */
    val slices: List<LoanSlice>,
) {
    val outstandingLent: Money get() = Money(outstandingLentMinor, currencyCode)
    val outstandingBorrowed: Money get() = Money(outstandingBorrowedMinor, currencyCode)

    /**
     * Lent minus borrowed: what the user would be left with if everything settled.
     *
     * Positive means the world owes them. Deliberately not shown alongside any account
     * balance, because none of this money is in their hands.
     */
    val netMinor: Long get() = outstandingLentMinor - outstandingBorrowedMinor
    val net: Money get() = Money(netMinor, currencyCode)

    val hasAnyOutstanding: Boolean get() = openCount > 0

    companion object {
        /** Enough to see the shape of it without turning the card into a second screen. */
        const val MAX_SLICES = 5
    }
}

/** The loan figures, recomputed whenever the ledger changes. */
class GetLoanSummaryUseCase(
    private val loans: LoanRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    operator fun invoke(): Flow<LoanSummary> = loans.loans.map { build(it, today()) }

    private fun build(all: List<Loan>, now: LocalDate): LoanSummary {
        val open = all.filterNot { it.isSettled }

        var lent = 0L
        var borrowed = 0L
        for (loan in open) {
            if (loan.direction.isLent) lent += loan.outstandingMinor
            else borrowed += loan.outstandingMinor
        }

        // One row per person per direction: someone can both owe the user and be owed
        // by them, and netting those two off would report a balance neither of them
        // would recognise.
        val byCounterparty = open
            .groupBy { it.counterparty to it.direction }
            .mapValues { (_, group) -> group.sumOf { it.outstandingMinor } }

        val largest = byCounterparty.values.maxOrNull() ?: 0L
        val slices = byCounterparty.entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<String, LoanDirection>, Long>> { it.value }
                    .thenBy { it.key.first },
            )
            .take(LoanSummary.MAX_SLICES)
            .map { (key, amount) ->
                val (counterparty, direction) = key
                LoanSlice(
                    counterparty = counterparty,
                    direction = direction,
                    amountMinor = amount,
                    currencyCode = all.firstOrNull()?.currencyCode ?: Repayment.CURRENCY,
                    // Relative to the biggest balance rather than to the total: with
                    // two people the bars would otherwise both sit near half width and
                    // say nothing about which debt is the one to chase.
                    share = if (largest <= 0L) {
                        0f
                    } else {
                        (amount.toDouble() / largest.toDouble()).toFloat()
                    },
                )
            }

        return LoanSummary(
            outstandingLentMinor = lent,
            outstandingBorrowedMinor = borrowed,
            currencyCode = all.firstOrNull()?.currencyCode ?: Repayment.CURRENCY,
            openCount = open.size,
            overdueCount = open.count { it.isOverdue(now) },
            slices = slices,
        )
    }
}
