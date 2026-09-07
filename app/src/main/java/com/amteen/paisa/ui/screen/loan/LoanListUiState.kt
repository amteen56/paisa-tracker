package com.amteen.paisa.ui.screen.loan

import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.usecase.LoanSummary
import java.time.LocalDate

/** Which loans the list is showing. */
enum class LoanFilter { OUTSTANDING, SETTLED, ALL }

/**
 * One row of the loans list, with everything it needs to render already worked out.
 *
 * The `is`/`Money` values come off the domain model, so the row does no arithmetic —
 * see CLAUDE.md.
 */
data class LoanRowState(
    val loan: Loan,
    val isOverdue: Boolean,
) {
    val id: String get() = loan.id
}

/**
 * The loans screen.
 *
 * [summary] is the same figure the dashboard card shows, from the same use case, so the
 * two can never disagree about what is still owed.
 */
data class LoanListUiState(
    val isLoading: Boolean = true,
    val filter: LoanFilter = LoanFilter.OUTSTANDING,
    val rows: List<LoanRowState> = emptyList(),
    /** Every loan, whatever the filter — so an empty filter reads differently to an empty ledger. */
    val totalLoanCount: Int = 0,
    val summary: LoanSummary? = null,
    val currency: Currency = CurrencyTable.fallback("PKR"),
    val today: LocalDate = LocalDate.now(),

    /** Payment methods offered when recording a repayment. Archived ones excluded. */
    val paymentMethods: List<PaymentMethod> = emptyList(),

    /** The loan whose repayment sheet is open, if any. */
    val repaymentFor: Loan? = null,
    val repaymentAmountInput: String = "",
    val repaymentAmountError: String? = null,
    val repaymentDate: LocalDate = LocalDate.now(),
    val repaymentPaymentMethodId: String? = null,
    val repaymentShowDatePicker: Boolean = false,
    val isSaving: Boolean = false,

    /** The loan a delete confirmation is open for. */
    val pendingDelete: Loan? = null,

    val error: String? = null,
) {
    /** True once loading has finished and there is genuinely nothing to show. */
    val isEmpty: Boolean get() = !isLoading && error == null && rows.isEmpty()

    /**
     * Whether the ledger is empty rather than the current filter being.
     *
     * "You have no loans yet" and "nothing outstanding" are different messages, and
     * showing the first when the user has ten settled loans reads as data loss.
     */
    val hasNoLoansAtAll: Boolean get() = !isLoading && totalLoanCount == 0

    val canSaveRepayment: Boolean
        get() = !isSaving && repaymentFor != null && repaymentAmountInput.isNotBlank()
}

sealed interface LoanListEvent {
    data class FilterChanged(val filter: LoanFilter) : LoanListEvent

    data class RepaymentStarted(val loanId: String) : LoanListEvent
    data object RepaymentDismissed : LoanListEvent
    data class RepaymentAmountChanged(val input: String) : LoanListEvent
    data class RepaymentDateChanged(val date: LocalDate) : LoanListEvent
    data class RepaymentPaymentMethodChanged(val id: String?) : LoanListEvent
    data object RepaymentOpenDatePicker : LoanListEvent
    data object RepaymentDismissDatePicker : LoanListEvent
    data object RepaymentSaved : LoanListEvent

    data class DeleteRequested(val loanId: String) : LoanListEvent
    data object DeleteDismissed : LoanListEvent
    data object DeleteConfirmed : LoanListEvent

    data object Retry : LoanListEvent
    data object DismissError : LoanListEvent
}
