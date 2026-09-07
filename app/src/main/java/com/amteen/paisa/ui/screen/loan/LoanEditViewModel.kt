package com.amteen.paisa.ui.screen.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.money.AmountParser
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.LoanRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.DeleteRepaymentUseCase
import com.amteen.paisa.domain.usecase.LoanInput
import com.amteen.paisa.domain.usecase.SaveLoanUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Backs both Add Loan and Edit Loan.
 *
 * On an existing loan the repayment history is shown below the fields, read-only apart
 * from a per-row delete — recording a repayment belongs on the list screen, where the
 * outstanding figure is in front of the user. What this screen offers is the way to
 * undo one that was typed wrong.
 */
class LoanEditViewModel(
    private val loanId: String?,
    private val loanRepository: LoanRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    private val saveLoan: SaveLoanUseCase,
    private val deleteRepayment: DeleteRepaymentUseCase,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoanEditUiState(isEditing = loanId != null))
    val uiState: StateFlow<LoanEditUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            paymentMethodRepository.load()
            loanRepository.load()

            val table = CurrencyTable(
                currencyRepository.currencies.value,
                settingsRepository.settings.value.baseCurrencyCode,
            )

            val loan = loanId?.let { loanRepository.getById(it) }
            if (loanId != null && loan == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "That loan no longer exists.")
                }
                return@launch
            }

            val currency = loan?.let { table.currency(it.currencyCode) } ?: table.base

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditing = loan != null,
                    counterpartyInput = loan?.counterparty.orEmpty(),
                    direction = loan?.direction ?: it.direction,
                    amountInput = loan?.let { l ->
                        amountToInput(l.principalMinor, currency.decimalDigits)
                    } ?: "",
                    currency = currency,
                    date = loan?.date ?: today(),
                    dueDate = loan?.dueDate,
                    noteInput = loan?.note.orEmpty(),
                    repayments = repaymentRows(loan),
                )
            }
        }
    }

    /** Newest first, each with its payment method resolved for display. */
    private fun repaymentRows(loan: Loan?): List<RepaymentRowState> {
        if (loan == null) return emptyList()
        val methodById = paymentMethodRepository.paymentMethods.value.associateBy { it.id }
        return loan.repayments
            .sortedWith(compareByDescending<com.amteen.paisa.domain.model.Repayment> { it.date }
                .thenBy { it.id })
            .map { RepaymentRowState(it, methodById[it.paymentMethodId]) }
    }

    fun onEvent(event: LoanEditEvent) {
        when (event) {
            is LoanEditEvent.CounterpartyChanged -> _uiState.update {
                it.copy(counterpartyInput = event.input, counterpartyError = null)
            }

            is LoanEditEvent.DirectionChanged -> _uiState.update {
                it.copy(direction = event.direction)
            }

            is LoanEditEvent.AmountChanged -> _uiState.update {
                it.copy(amountInput = event.input, amountError = null)
            }

            is LoanEditEvent.NoteChanged -> _uiState.update { it.copy(noteInput = event.input) }

            is LoanEditEvent.OpenDatePicker -> _uiState.update {
                it.copy(pickingDate = event.field)
            }

            LoanEditEvent.DismissDatePicker -> _uiState.update { it.copy(pickingDate = null) }

            is LoanEditEvent.DatePicked -> _uiState.update { state ->
                when (state.pickingDate) {
                    LoanEditUiState.DateField.DUE -> state.copy(
                        dueDate = event.date,
                        pickingDate = null,
                        dueDateError = null,
                    )
                    // Moving the loan's own date can invalidate a due date behind it,
                    // so the field error is cleared and the use case checks it again.
                    else -> state.copy(
                        date = event.date,
                        pickingDate = null,
                        dueDateError = null,
                    )
                }
            }

            LoanEditEvent.DueDateCleared -> _uiState.update {
                it.copy(dueDate = null, dueDateError = null)
            }

            is LoanEditEvent.RepaymentDeleted -> removeRepayment(event.repaymentId)

            LoanEditEvent.Save -> save()

            LoanEditEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun removeRepayment(repaymentId: String) {
        val id = loanId ?: return
        viewModelScope.launch {
            when (val result = deleteRepayment(id, repaymentId)) {
                is AppResult.Ok -> _uiState.update {
                    it.copy(repayments = repaymentRows(result.value))
                }
                is AppResult.Err -> _uiState.update {
                    it.copy(error = result.error.displayMessage)
                }
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val parsed = AmountParser.parse(state.amountInput, state.currency)
        if (parsed !is AmountParser.Result.Valid) {
            _uiState.update {
                it.copy(
                    amountError = amountErrorMessage(
                        (parsed as AmountParser.Result.Invalid).reason,
                        state.currency.decimalDigits,
                    ),
                )
            }
            return
        }

        _uiState.update {
            it.copy(isSaving = true, amountError = null, counterpartyError = null)
        }

        viewModelScope.launch {
            val result = saveLoan(
                LoanInput(
                    id = loanId,
                    counterparty = state.counterpartyInput,
                    direction = state.direction,
                    principalMinor = parsed.amountMinor,
                    currencyCode = state.currency.code,
                    date = state.date,
                    dueDate = state.dueDate,
                    note = state.noteInput,
                ),
            )

            when (result) {
                is AppResult.Ok -> _uiState.update { it.copy(isSaving = false, finished = true) }
                is AppResult.Err -> _uiState.update { current ->
                    current.copy(isSaving = false).withError(result.error)
                }
            }
        }
    }

    /** Routes a field error back to the field that caused it, not to a banner. */
    private fun LoanEditUiState.withError(error: AppError): LoanEditUiState {
        if (error !is AppError.Validation) return copy(error = error.displayMessage)
        return when (error.field) {
            SaveLoanUseCase.FIELD_COUNTERPARTY -> copy(counterpartyError = error.message)
            SaveLoanUseCase.FIELD_AMOUNT -> copy(amountError = error.message)
            SaveLoanUseCase.FIELD_DUE_DATE -> copy(dueDateError = error.message)
            else -> copy(error = error.message)
        }
    }

    private fun amountErrorMessage(reason: AmountParser.Reason, decimalDigits: Int): String =
        when (reason) {
            AmountParser.Reason.EMPTY -> "Enter an amount."
            AmountParser.Reason.NOT_A_NUMBER -> "That is not a number."
            AmountParser.Reason.TOO_MANY_DECIMAL_POINTS -> "Only one decimal point."
            AmountParser.Reason.TOO_MANY_DECIMAL_DIGITS -> "Only $decimalDigits decimal places."
            AmountParser.Reason.NOT_POSITIVE -> "Enter an amount greater than zero."
            AmountParser.Reason.TOO_LARGE -> "That amount is too large."
        }

    /** Renders a stored amount back into the raw text the field expects. */
    private fun amountToInput(amountMinor: Long, decimalDigits: Int): String {
        if (decimalDigits == 0) return amountMinor.toString()
        val scale = generateSequence(1L) { it * 10 }.elementAt(decimalDigits)
        return "${amountMinor / scale}." +
            (amountMinor % scale).toString().padStart(decimalDigits, '0')
    }
}
