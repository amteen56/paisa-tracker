package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Repayment
import com.amteen.paisa.testing.FakeLoanRepository
import com.amteen.paisa.testing.FakePaymentMethodRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Lending, borrowing and getting money back.
 *
 * The rules worth pinning down are the ones about repayments: partial ones are normal,
 * an over-repayment is a typo rather than a generosity to be absorbed, and settlement
 * is derived from the history rather than being a flag anything can set.
 */
class LoanUseCasesTest {

    private val today = LocalDate.of(2026, 9, 12)

    private lateinit var loans: FakeLoanRepository
    private lateinit var methods: FakePaymentMethodRepository
    private lateinit var save: SaveLoanUseCase
    private lateinit var repay: RecordRepaymentUseCase
    private lateinit var deleteRepayment: DeleteRepaymentUseCase
    private lateinit var delete: DeleteLoanUseCase
    private var nextId = 0

    @Before
    fun setUp() {
        loans = FakeLoanRepository()
        methods = FakePaymentMethodRepository(listOf(PaymentMethod("pm-cash", "Cash", "cash")))
        nextId = 0
        save = SaveLoanUseCase(loans) { "generated-${nextId++}" }
        repay = RecordRepaymentUseCase(loans, methods) { "repay-${nextId++}" }
        deleteRepayment = DeleteRepaymentUseCase(loans)
        delete = DeleteLoanUseCase(loans)
    }

    private fun input(
        id: String? = null,
        counterparty: String = "Ali",
        direction: LoanDirection = LoanDirection.LENT,
        principalMinor: Long = 500_000,
        date: LocalDate = today,
        dueDate: LocalDate? = null,
        note: String = "",
    ) = LoanInput(
        id = id,
        counterparty = counterparty,
        direction = direction,
        principalMinor = principalMinor,
        date = date,
        dueDate = dueDate,
        note = note,
    )

    private fun errorOf(result: AppResult<*>): AppError.Validation =
        result.errorOrNull() as AppError.Validation

    private suspend fun saved(input: LoanInput = input()): Loan =
        (save(input) as AppResult.Ok).value

    // -- Saving -------------------------------------------------------------

    @Test
    fun `a new loan is stored with a generated id`() = runTest {
        val loan = saved(input(dueDate = today.plusDays(10), note = "  bike repair  "))

        assertEquals("generated-0", loan.id)
        assertEquals("Ali", loan.counterparty)
        assertEquals(500_000L, loan.principalMinor)
        assertEquals("PKR", loan.currencyCode)
        assertEquals(today.plusDays(10), loan.dueDate)
        // Trimmed, so a stray space cannot make "Ali" and "Ali " two different people
        // in the outstanding-by-person breakdown.
        assertEquals("bike repair", loan.note)
        assertEquals(loan, loans.loans.value.single())
    }

    @Test
    fun `a loan with no one attached is rejected`() = runTest {
        val error = errorOf(save(input(counterparty = "   ")))
        assertEquals(SaveLoanUseCase.FIELD_COUNTERPARTY, error.field)
    }

    @Test
    fun `a zero or negative amount is rejected`() = runTest {
        assertEquals(
            SaveLoanUseCase.FIELD_AMOUNT,
            errorOf(save(input(principalMinor = 0))).field,
        )
        assertEquals(
            SaveLoanUseCase.FIELD_AMOUNT,
            errorOf(save(input(principalMinor = -100))).field,
        )
    }

    @Test
    fun `a due date before the loan was made is rejected`() = runTest {
        val error = errorOf(save(input(dueDate = today.minusDays(1))))
        assertEquals(SaveLoanUseCase.FIELD_DUE_DATE, error.field)
    }

    @Test
    fun `a due date on the same day is allowed`() = runTest {
        assertTrue(save(input(dueDate = today)).isOk)
    }

    @Test
    fun `editing a loan keeps its repayments`() = runTest {
        val loan = saved()
        repay(loan.id, 200_000, today, "pm-cash")

        val edited = saved(input(id = loan.id, counterparty = "Ali Raza"))

        // The form does not own the history. Correcting a name must not wipe the
        // record of what has already come back.
        assertEquals("Ali Raza", edited.counterparty)
        assertEquals(200_000L, edited.repaidMinor)
        assertEquals(1, loans.loans.value.size)
    }

    @Test
    fun `dropping the amount below what has already come back is refused`() = runTest {
        val loan = saved()
        repay(loan.id, 300_000, today, "pm-cash")

        // Clamping instead would throw away the figure the user typed and leave them
        // with a loan that is silently more than settled.
        val error = errorOf(save(input(id = loan.id, principalMinor = 100_000)))
        assertEquals(SaveLoanUseCase.FIELD_AMOUNT, error.field)
        assertEquals(500_000L, loans.loans.value.single().principalMinor)
    }

    @Test
    fun `raising the amount to exactly what came back is allowed`() = runTest {
        val loan = saved()
        repay(loan.id, 300_000, today, "pm-cash")

        val edited = saved(input(id = loan.id, principalMinor = 300_000))

        assertTrue(edited.isSettled)
        assertEquals(0L, edited.outstandingMinor)
    }

    // -- Repayments ---------------------------------------------------------

    @Test
    fun `a partial repayment reduces what is outstanding without settling the loan`() = runTest {
        val loan = saved()

        val updated = (repay(loan.id, 200_000, today, "pm-cash") as AppResult.Ok).value

        assertEquals(200_000L, updated.repaidMinor)
        assertEquals(300_000L, updated.outstandingMinor)
        assertFalse(updated.isSettled)
        assertEquals(0.4f, updated.repaidFraction, 0.0001f)
    }

    @Test
    fun `repayments accumulate and the last one settles the loan by itself`() = runTest {
        val loan = saved()

        repay(loan.id, 200_000, today.minusDays(3), "pm-cash")
        repay(loan.id, 100_000, today.minusDays(1), null)
        val final = (repay(loan.id, 200_000, today, "pm-cash") as AppResult.Ok).value

        // Nothing marks a loan settled: the repayments adding up to the principal is
        // what settlement *is*. A stored flag could disagree with the history.
        assertEquals(3, final.repayments.size)
        assertEquals(0L, final.outstandingMinor)
        assertTrue(final.isSettled)
    }

    @Test
    fun `a repayment larger than what is owed is refused`() = runTest {
        val loan = saved()

        // Someone typing 5,000,000 for a 5,000 loan has made a typo. Quietly taking
        // the 5,000 of it that fits would bury the mistake in a record they trust.
        val error = errorOf(repay(loan.id, 5_000_000, today, "pm-cash"))
        assertEquals(RecordRepaymentUseCase.FIELD_AMOUNT, error.field)
        assertTrue(loans.loans.value.single().repayments.isEmpty())
    }

    @Test
    fun `a repayment larger than what remains is refused, not just one over the principal`() =
        runTest {
            val loan = saved()
            repay(loan.id, 400_000, today, "pm-cash")

            // 200,000 is under the 500,000 principal but over the 100,000 left.
            val error = errorOf(repay(loan.id, 200_000, today, "pm-cash"))
            assertEquals(RecordRepaymentUseCase.FIELD_AMOUNT, error.field)
            assertEquals(100_000L, loans.loans.value.single().outstandingMinor)
        }

    @Test
    fun `a zero repayment is refused`() = runTest {
        val loan = saved()
        assertEquals(
            RecordRepaymentUseCase.FIELD_AMOUNT,
            errorOf(repay(loan.id, 0, today, "pm-cash")).field,
        )
    }

    @Test
    fun `a repayment against an already settled loan is refused`() = runTest {
        val loan = saved()
        repay(loan.id, 500_000, today, "pm-cash")

        assertEquals(
            RecordRepaymentUseCase.FIELD_AMOUNT,
            errorOf(repay(loan.id, 100, today, "pm-cash")).field,
        )
    }

    @Test
    fun `a repayment naming a payment method that no longer exists is refused`() = runTest {
        val loan = saved()

        // Otherwise the repayment would hold an id that resolves to nothing, which is
        // the dangling reference CLAUDE.md rule 4 exists to prevent.
        val error = errorOf(repay(loan.id, 100_000, today, "pm-gone"))
        assertEquals(RecordRepaymentUseCase.FIELD_PAYMENT_METHOD, error.field)
    }

    @Test
    fun `a repayment with no payment method is allowed`() = runTest {
        val loan = saved()

        val updated = (repay(loan.id, 100_000, today, null) as AppResult.Ok).value

        assertEquals(100_000L, updated.repaidMinor)
        assertEquals(null, updated.repayments.single().paymentMethodId)
    }

    @Test
    fun `a repayment against a loan that is gone reports not found`() = runTest {
        val result = repay("nope", 100, today, null)
        assertTrue(result.errorOrNull() is AppError.NotFound)
    }

    @Test
    fun `removing a repayment puts the amount back outstanding`() = runTest {
        val loan = saved()
        val withRepayment = (repay(loan.id, 500_000, today, "pm-cash") as AppResult.Ok).value
        assertTrue(withRepayment.isSettled)

        val repaymentId = withRepayment.repayments.single().id
        val updated = (deleteRepayment(loan.id, repaymentId) as AppResult.Ok).value

        // The way out of a mistyped repayment, without deleting the loan and
        // re-entering the whole history.
        assertFalse(updated.isSettled)
        assertEquals(500_000L, updated.outstandingMinor)
        assertTrue(updated.repayments.isEmpty())
    }

    @Test
    fun `removing a repayment that is not there reports not found`() = runTest {
        val loan = saved()
        assertTrue(deleteRepayment(loan.id, "nope").errorOrNull() is AppError.NotFound)
    }

    // -- Deleting -----------------------------------------------------------

    @Test
    fun `deleting a loan needs no reference check`() = runTest {
        val loan = saved()
        repay(loan.id, 100_000, today, "pm-cash")

        // Nothing points at a loan — no transaction, budget or report — so there is
        // nothing to orphan.
        assertTrue(delete(loan.id).isOk)
        assertTrue(loans.loans.value.isEmpty())
    }

    // -- Summary ------------------------------------------------------------

    private fun loan(
        id: String,
        counterparty: String,
        direction: LoanDirection,
        principalMinor: Long,
        dueDate: LocalDate? = null,
        repayments: List<Repayment> = emptyList(),
    ) = Loan(
        id = id,
        counterparty = counterparty,
        direction = direction,
        principalMinor = principalMinor,
        currencyCode = "PKR",
        date = today.minusDays(30),
        dueDate = dueDate,
        repayments = repayments,
    )

    private suspend fun summary(vararg all: Loan) =
        GetLoanSummaryUseCase(FakeLoanRepository(all.toList())) { today }().first()

    @Test
    fun `an empty ledger reports zeroes rather than failing`() = runTest {
        val result = summary()

        assertEquals(0L, result.outstandingLentMinor)
        assertEquals(0L, result.outstandingBorrowedMinor)
        assertEquals(0, result.openCount)
        assertFalse(result.hasAnyOutstanding)
        assertTrue(result.slices.isEmpty())
    }

    @Test
    fun `the two directions are totalled separately and netted`() = runTest {
        val result = summary(
            loan("a", "Ali", LoanDirection.LENT, 500_000),
            loan("b", "Bilal", LoanDirection.LENT, 100_000),
            loan("c", "Hina", LoanDirection.BORROWED, 300_000),
        )

        assertEquals(600_000L, result.outstandingLentMinor)
        assertEquals(300_000L, result.outstandingBorrowedMinor)
        assertEquals(300_000L, result.netMinor)
        assertEquals(3, result.openCount)
    }

    @Test
    fun `only what is still owed counts, and a settled loan drops out entirely`() = runTest {
        val result = summary(
            loan(
                "a", "Ali", LoanDirection.LENT, 500_000,
                repayments = listOf(Repayment("r1", 200_000, today)),
            ),
            loan(
                "b", "Bilal", LoanDirection.LENT, 100_000,
                repayments = listOf(Repayment("r2", 100_000, today)),
            ),
        )

        assertEquals(300_000L, result.outstandingLentMinor)
        assertEquals(1, result.openCount)
        assertEquals(listOf("Ali"), result.slices.map { it.counterparty })
    }

    @Test
    fun `a loan is overdue only once its due date has passed with money still owed`() = runTest {
        val result = summary(
            loan("past", "Ali", LoanDirection.LENT, 100_000, dueDate = today.minusDays(1)),
            loan("today", "Bilal", LoanDirection.LENT, 100_000, dueDate = today),
            loan("future", "Hina", LoanDirection.LENT, 100_000, dueDate = today.plusDays(1)),
            loan("none", "Zara", LoanDirection.LENT, 100_000),
            loan(
                "paid", "Omar", LoanDirection.LENT, 100_000,
                dueDate = today.minusDays(10),
                repayments = listOf(Repayment("r1", 100_000, today)),
            ),
        )

        // Due today is not late, and a loan that came back late is not still overdue.
        assertEquals(1, result.overdueCount)
    }

    @Test
    fun `the same person lending and borrowing is two rows, not one netted off`() = runTest {
        val result = summary(
            loan("a", "Ali", LoanDirection.LENT, 500_000),
            loan("b", "Ali", LoanDirection.BORROWED, 200_000),
        )

        // Netting these to "Ali owes 300,000" would report a balance neither of them
        // would recognise, and hide the 200,000 the user has to pay back.
        assertEquals(2, result.slices.size)
        assertEquals(500_000L, result.outstandingLentMinor)
        assertEquals(200_000L, result.outstandingBorrowedMinor)
    }

    @Test
    fun `several loans to one person in the same direction are one row`() = runTest {
        val result = summary(
            loan("a", "Ali", LoanDirection.LENT, 500_000),
            loan("b", "Ali", LoanDirection.LENT, 200_000),
        )

        assertEquals(1, result.slices.size)
        assertEquals(700_000L, result.slices.single().amountMinor)
    }

    @Test
    fun `slices are biggest first, scaled against the largest, and capped`() = runTest {
        val result = summary(
            *(1..8).map { index ->
                loan("l$index", "P$index", LoanDirection.LENT, index * 100_000L)
            }.toTypedArray(),
        )

        assertEquals(LoanSummary.MAX_SLICES, result.slices.size)
        assertEquals(800_000L, result.slices.first().amountMinor)
        // Relative to the biggest balance, so the debt worth chasing is the full bar.
        assertEquals(1f, result.slices.first().share, 0.0001f)
        assertEquals(0.5f, result.slices.last().share, 0.0001f)
    }
}
