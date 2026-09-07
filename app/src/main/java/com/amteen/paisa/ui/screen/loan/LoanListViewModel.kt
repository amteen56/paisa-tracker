package com.amteen.paisa.ui.screen.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.money.AmountParser
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.LoanRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.DeleteLoanUseCase
import com.amteen.paisa.domain.usecase.GetLoanSummaryUseCase
import com.amteen.paisa.domain.usecase.RecordRepaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The loans list, its filter, and the repayment sheet.
 *
 * The list and the header figures both come off [GetLoanSummaryUseCase] and the loan
 * flow, so nothing here sums anything. What the ViewModel does own is the sheet: which
 * loan it is for, the raw amount text, and the chosen date and method.
 */
class LoanListViewModel(
    private val loanRepository: LoanRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    getLoanSummary: GetLoanSummaryUseCase,
    private val recordRepayment: RecordRepaymentUseCase,
    private val deleteLoan: DeleteLoanUseCase,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** Everything the screen owns rather than derives. */
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<LoanListUiState> = combine(
        loanRepository.loans,
        getLoanSummary(),
        paymentMethodRepository.paymentMethods,
        currencyRepository.currencies,
        local,
    ) { loans, summary, methods, currencies, own ->
        val now = today()
        val table = CurrencyTable(currencies, settingsRepository.settings.value.baseCurrencyCode)

        val visible = loans.filter { loan ->
            when (own.filter) {
                LoanFilter.OUTSTANDING -> !loan.isSettled
                LoanFilter.SETTLED -> loan.isSettled
                LoanFilter.ALL -> true
            }
        }

        LoanListUiState(
            isLoading = false,
            filter = own.filter,
            rows = visible.map { LoanRowState(loan = it, isOverdue = it.isOverdue(now)) },
            totalLoanCount = loans.size,
            summary = summary,
            currency = table.base,
            today = now,
            paymentMethods = methods.filterNot { it.archived },
            // Resolved from the live list rather than held as a copy, so the sheet's
            // outstanding figure follows a repayment recorded while it is open.
            repaymentFor = own.repaymentForId?.let { id -> loans.firstOrNull { it.id == id } },
            repaymentAmountInput = own.repaymentAmountInput,
            repaymentAmountError = own.repaymentAmountError,
            repaymentDate = own.repaymentDate ?: now,
            repaymentPaymentMethodId = own.repaymentPaymentMethodId,
            repaymentShowDatePicker = own.repaymentShowDatePicker,
            isSaving = own.isSaving,
            pendingDelete = own.pendingDeleteId?.let { id -> loans.firstOrNull { it.id == id } },
            error = own.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LoanListUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            paymentMethodRepository.load()
            loanRepository.load()
        }
    }

    fun onEvent(event: LoanListEvent) {
        when (event) {
            is LoanListEvent.FilterChanged -> local.update { it.copy(filter = event.filter) }

            is LoanListEvent.RepaymentStarted -> startRepayment(event.loanId)

            LoanListEvent.RepaymentDismissed -> local.update {
                it.copy(
                    repaymentForId = null,
                    repaymentAmountInput = "",
                    repaymentAmountError = null,
                    repaymentShowDatePicker = false,
                )
            }

            is LoanListEvent.RepaymentAmountChanged -> local.update {
                it.copy(repaymentAmountInput = event.input, repaymentAmountError = null)
            }

            is LoanListEvent.RepaymentDateChanged -> local.update {
                it.copy(repaymentDate = event.date, repaymentShowDatePicker = false)
            }

            is LoanListEvent.RepaymentPaymentMethodChanged -> local.update {
                // Tapping the selected chip again clears it: a repayment does not have
                // to say how the money moved.
                val next = if (it.repaymentPaymentMethodId == event.id) null else event.id
                it.copy(repaymentPaymentMethodId = next)
            }

            LoanListEvent.RepaymentOpenDatePicker -> local.update {
                it.copy(repaymentShowDatePicker = true)
            }

            LoanListEvent.RepaymentDismissDatePicker -> local.update {
                it.copy(repaymentShowDatePicker = false)
            }

            LoanListEvent.RepaymentSaved -> saveRepayment()

            is LoanListEvent.DeleteRequested -> local.update {
                it.copy(pendingDeleteId = event.loanId)
            }

            LoanListEvent.DeleteDismissed -> local.update { it.copy(pendingDeleteId = null) }

            LoanListEvent.DeleteConfirmed -> confirmDelete()

            LoanListEvent.Retry -> viewModelScope.launch {
                local.update { it.copy(error = null) }
                loanRepository.load()
            }

            LoanListEvent.DismissError -> local.update { it.copy(error = null) }
        }
    }

    /**
     * Opens the sheet with the outstanding amount already filled in.
     *
     * "They paid it all back" is the common case, so it should be one tap and a
     * confirm rather than retyping a figure that is already on screen.
     */
    private fun startRepayment(loanId: String) {
        val loan = loanRepository.loans.value.firstOrNull { it.id == loanId } ?: return
        val currency = CurrencyTable(
            currencyRepository.currencies.value,
            settingsRepository.settings.value.baseCurrencyCode,
        ).currency(loan.currencyCode)

        local.update {
            it.copy(
                repaymentForId = loanId,
                repaymentAmountInput = amountToInput(
                    loan.outstandingMinor,
                    currency.decimalDigits,
                ),
                repaymentAmountError = null,
                repaymentDate = today(),
                repaymentPaymentMethodId = settingsRepository.settings.value.defaultPaymentMethodId,
                repaymentShowDatePicker = false,
            )
        }
    }

    private fun saveRepayment() {
        val own = local.value
        val loanId = own.repaymentForId ?: return
        if (own.isSaving) return

        val loan = loanRepository.loans.value.firstOrNull { it.id == loanId } ?: return
        val currency = CurrencyTable(
            currencyRepository.currencies.value,
            settingsRepository.settings.value.baseCurrencyCode,
        ).currency(loan.currencyCode)

        val parsed = AmountParser.parse(own.repaymentAmountInput, currency)
        if (parsed !is AmountParser.Result.Valid) {
            local.update {
                it.copy(
                    repaymentAmountError = amountErrorMessage(
                        (parsed as AmountParser.Result.Invalid).reason,
                        currency.decimalDigits,
                    ),
                )
            }
            return
        }

        local.update { it.copy(isSaving = true, repaymentAmountError = null) }

        viewModelScope.launch {
            val result = recordRepayment(
                loanId = loanId,
                amountMinor = parsed.amountMinor,
                date = own.repaymentDate ?: today(),
                paymentMethodId = own.repaymentPaymentMethodId,
            )

            when (result) {
                is AppResult.Ok -> local.update {
                    it.copy(
                        isSaving = false,
                        repaymentForId = null,
                        repaymentAmountInput = "",
                        repaymentAmountError = null,
                    )
                }
                is AppResult.Err -> local.update { current ->
                    current.copy(isSaving = false).withError(result.error)
                }
            }
        }
    }

    private fun confirmDelete() {
        val id = local.value.pendingDeleteId ?: return
        viewModelScope.launch {
            local.update { it.copy(pendingDeleteId = null) }
            when (val result = deleteLoan(id)) {
                is AppResult.Ok -> Unit
                is AppResult.Err -> local.update {
                    it.copy(error = result.error.displayMessage)
                }
            }
        }
    }

    /** Routes a field error to the field, not to a banner. */
    private fun LocalState.withError(error: AppError): LocalState {
        if (error !is AppError.Validation) return copy(error = error.displayMessage)
        return when (error.field) {
            RecordRepaymentUseCase.FIELD_AMOUNT -> copy(repaymentAmountError = error.message)
            else -> copy(error = error.message)
        }
    }

    private fun amountErrorMessage(reason: AmountParser.Reason, decimalDigits: Int): String =
        when (reason) {
            AmountParser.Reason.EMPTY -> "Enter an amount."
            AmountParser.Reason.NOT_A_NUMBER -> "That is not a number."
            AmountParser.Reason.TOO_MANY_DECIMAL_POINTS -> "Only one decimal point."
            AmountParser.Reason.TOO_MANY_DECIMAL_DIGITS ->
                "Only $decimalDigits decimal places."
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

    /**
     * What the screen owns, kept apart from what it derives.
     *
     * Loans are referenced by id rather than held as copies, so a row and the sheet
     * above it can never end up showing two different versions of the same loan.
     */
    private data class LocalState(
        val filter: LoanFilter = LoanFilter.OUTSTANDING,
        val repaymentForId: String? = null,
        val repaymentAmountInput: String = "",
        val repaymentAmountError: String? = null,
        val repaymentDate: LocalDate? = null,
        val repaymentPaymentMethodId: String? = null,
        val repaymentShowDatePicker: Boolean = false,
        val isSaving: Boolean = false,
        val pendingDeleteId: String? = null,
        val error: String? = null,
    )
}
