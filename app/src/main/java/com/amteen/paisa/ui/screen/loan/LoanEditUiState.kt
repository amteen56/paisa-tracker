package com.amteen.paisa.ui.screen.loan

import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Repayment
import java.time.LocalDate

/** One row of the repayment history, with its method already resolved. */
data class RepaymentRowState(
    val repayment: Repayment,
    /** Null when the method was deleted, or when none was recorded. */
    val paymentMethod: PaymentMethod?,
) {
    val id: String get() = repayment.id
}

/**
 * The loan form.
 *
 * [amountInput] is raw text, not a parsed amount: a form in progress is allowed to hold
 * "1,2" while the user is still typing. Parsing happens once, on save, through
 * `AmountParser` — the same rule the transaction and budget forms follow.
 */
data class LoanEditUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val finished: Boolean = false,

    val counterpartyInput: String = "",
    val counterpartyError: String? = null,

    val direction: LoanDirection = LoanDirection.LENT,

    val amountInput: String = "",
    val amountError: String? = null,
    val currency: Currency = CurrencyTable.fallback("PKR"),

    val date: LocalDate = LocalDate.now(),
    val dueDate: LocalDate? = null,
    val dueDateError: String? = null,

    val noteInput: String = "",

    /** Which date field the picker is currently editing. Null means closed. */
    val pickingDate: DateField? = null,

    /** Newest first. Empty on a loan that has not been saved yet. */
    val repayments: List<RepaymentRowState> = emptyList(),

    val error: String? = null,
) {
    enum class DateField { LOANED, DUE }

    val canSave: Boolean
        get() = !isSaving && counterpartyInput.isNotBlank() && amountInput.isNotBlank()

    /**
     * The date the picker should open on.
     *
     * A due date that has not been set opens on the loan's own date rather than today,
     * because "a fortnight after I lent it" is the thing people are usually reaching for.
     */
    val pickerInitialDate: LocalDate
        get() = when (pickingDate) {
            DateField.DUE -> dueDate ?: date
            else -> date
        }
}

sealed interface LoanEditEvent {
    data class CounterpartyChanged(val input: String) : LoanEditEvent
    data class DirectionChanged(val direction: LoanDirection) : LoanEditEvent
    data class AmountChanged(val input: String) : LoanEditEvent
    data class NoteChanged(val input: String) : LoanEditEvent

    data class OpenDatePicker(val field: LoanEditUiState.DateField) : LoanEditEvent
    data object DismissDatePicker : LoanEditEvent
    data class DatePicked(val date: LocalDate) : LoanEditEvent

    /** Back to "no due date" without having to pick one. */
    data object DueDateCleared : LoanEditEvent

    data class RepaymentDeleted(val repaymentId: String) : LoanEditEvent

    data object Save : LoanEditEvent
    data object DismissError : LoanEditEvent
}
