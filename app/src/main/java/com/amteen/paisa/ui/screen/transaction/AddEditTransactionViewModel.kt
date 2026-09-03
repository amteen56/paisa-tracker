package com.amteen.paisa.ui.screen.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.money.AmountParser
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.GetTransactionDetailsUseCase
import com.amteen.paisa.domain.usecase.SaveTransactionUseCase
import com.amteen.paisa.domain.usecase.TransactionInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Backs both Add and Edit — the two differ only in whether an existing record is
 * loaded first, so one form avoids two implementations drifting apart.
 *
 * Does no file I/O and holds no Android types: it calls use cases and repositories
 * and exposes one [StateFlow]. See CLAUDE.md.
 */
class AddEditTransactionViewModel(
    private val transactionId: String?,
    initialType: TransactionType,
    private val saveTransaction: SaveTransactionUseCase,
    private val getTransactionDetails: GetTransactionDetailsUseCase,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditTransactionUiState(
            type = initialType,
            isEditing = transactionId != null,
        ),
    )
    val uiState: StateFlow<AddEditTransactionUiState> = _uiState.asStateFlow()

    /** All categories, unfiltered; the visible list is narrowed by type. */
    private var allCategories: List<Category> = emptyList()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()

            val settings = settingsRepository.settings.value
            val table = CurrencyTable(currencyRepository.currencies.value, settings.baseCurrencyCode)
            allCategories = categoryRepository.categories.value

            if (transactionId == null) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        currency = table.base,
                        currencies = table.active,
                        categories = categoriesFor(state.type),
                        paymentMethods = paymentMethodRepository.paymentMethods.value
                            .filterNot { it.archived },
                        // Pre-selecting the default payment method removes a tap
                        // from the most common path.
                        selectedPaymentMethodId = settings.defaultPaymentMethodId,
                    )
                }
                return@launch
            }

            when (val result = getTransactionDetails(transactionId)) {
                is AppResult.Err -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }

                is AppResult.Ok -> {
                    val details = result.value
                    val record = details.transaction
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditing = true,
                            type = record.type,
                            amountInput = amountToInput(record.amountMinor, details.currency.decimalDigits),
                            currency = details.currency,
                            currencies = table.active,
                            categories = categoriesFor(record.type, record.categoryId),
                            selectedCategoryId = record.categoryId,
                            selectedSubcategoryId = record.subcategoryId,
                            description = record.description,
                            date = record.date,
                            time = record.time,
                            paymentMethods = paymentMethodRepository.paymentMethods.value
                                .filterNot { pm -> pm.archived },
                            selectedPaymentMethodId = record.paymentMethodId,
                            notes = record.notes.orEmpty(),
                            notesVisible = !record.notes.isNullOrBlank(),
                        )
                    }
                }
            }
        }
    }

    fun onEvent(event: AddEditTransactionEvent) {
        when (event) {
            is AddEditTransactionEvent.TypeChanged -> onTypeChanged(event.type)

            is AddEditTransactionEvent.AmountChanged -> _uiState.update {
                it.copy(amountInput = event.input, amountError = null)
            }

            is AddEditTransactionEvent.CurrencySelected -> _uiState.update { state ->
                val currency = state.currencies.firstOrNull { it.code == event.code }
                    ?: return@update state
                state.copy(currency = currency, amountError = null)
            }

            is AddEditTransactionEvent.CategorySelected -> _uiState.update {
                // Changing category invalidates any subcategory chosen under the old one.
                it.copy(selectedCategoryId = event.id, selectedSubcategoryId = null)
            }

            is AddEditTransactionEvent.SubcategorySelected -> _uiState.update {
                // Tapping the selected chip again clears it.
                val next = if (it.selectedSubcategoryId == event.id) null else event.id
                it.copy(selectedSubcategoryId = next)
            }

            is AddEditTransactionEvent.DescriptionChanged -> _uiState.update {
                it.copy(description = event.text)
            }

            is AddEditTransactionEvent.DateSelected -> _uiState.update {
                it.copy(date = event.date, showDatePicker = false)
            }

            is AddEditTransactionEvent.PaymentMethodSelected -> _uiState.update {
                val next = if (it.selectedPaymentMethodId == event.id) null else event.id
                it.copy(selectedPaymentMethodId = next)
            }

            is AddEditTransactionEvent.NotesChanged -> _uiState.update {
                it.copy(notes = event.text)
            }

            AddEditTransactionEvent.ToggleNotes -> _uiState.update {
                it.copy(notesVisible = !it.notesVisible, notes = if (it.notesVisible) "" else it.notes)
            }

            AddEditTransactionEvent.OpenDatePicker -> _uiState.update {
                it.copy(showDatePicker = true)
            }

            AddEditTransactionEvent.DismissDatePicker -> _uiState.update {
                it.copy(showDatePicker = false)
            }

            AddEditTransactionEvent.DismissError -> _uiState.update { it.copy(error = null) }

            AddEditTransactionEvent.Save -> save()
        }
    }

    private fun onTypeChanged(type: TransactionType) {
        _uiState.update { state ->
            if (state.type == type) return@update state
            val categories = categoriesFor(type, state.selectedCategoryId)
            // An expense category is not valid for income, so a switch clears the
            // selection rather than silently keeping an inapplicable one.
            val keepCategory = state.selectedCategoryId
                ?.takeIf { id -> categories.any { it.id == id } }
            state.copy(
                type = type,
                categories = categories,
                selectedCategoryId = keepCategory,
                selectedSubcategoryId = if (keepCategory == null) null else state.selectedSubcategoryId,
            )
        }
    }

    /**
     * The categories to offer for [type].
     *
     * Archived ones are out of the picker, with one exception: the category
     * [selectedId] names stays in even when archived, so editing an old transaction
     * whose category has since been archived still shows what it is filed under.
     * Otherwise the chip row would render with nothing selected and the user would
     * have no way to tell the selection was still intact. See CLAUDE.md rule 4 —
     * archived items must keep rendering correctly.
     */
    private fun categoriesFor(
        type: TransactionType,
        selectedId: String? = null,
    ): List<Category> {
        val active = allCategories.filter { !it.archived && it.applicableTo.allows(type) }
        // Archiving is forgiven; inapplicability is not. An expense-only category
        // must still drop out when the form switches to income, or a type switch
        // would leave an impossible selection in place.
        val selected = selectedId
            ?.let { id -> allCategories.firstOrNull { it.id == id } }
            ?.takeIf { it.applicableTo.allows(type) }
        return if (selected != null && active.none { it.id == selected.id }) {
            active + selected
        } else {
            active
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val parsed = AmountParser.parse(state.amountInput, state.currency)
        if (parsed !is AmountParser.Result.Valid) {
            _uiState.update {
                it.copy(amountError = amountErrorMessage((parsed as AmountParser.Result.Invalid).reason))
            }
            return
        }

        val categoryId = state.selectedCategoryId
        if (categoryId == null) {
            _uiState.update { it.copy(error = "Choose a category first.") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val result = saveTransaction(
                TransactionInput(
                    id = transactionId,
                    type = state.type,
                    amountMinor = parsed.amountMinor,
                    currencyCode = state.currency.code,
                    categoryId = categoryId,
                    subcategoryId = state.selectedSubcategoryId,
                    description = state.description,
                    date = state.date,
                    time = state.time,
                    paymentMethodId = state.selectedPaymentMethodId,
                    notes = state.notes.takeIf { state.notesVisible && it.isNotBlank() },
                ),
            )

            when (result) {
                is AppResult.Ok -> _uiState.update { it.copy(isSaving = false, finished = true) }

                is AppResult.Err -> _uiState.update { current ->
                    val error = result.error
                    // Route a field error back to the field so the form can point
                    // at it, and anything else to the screen-level banner.
                    if (error is AppError.Validation &&
                        error.field == SaveTransactionUseCase.FIELD_AMOUNT
                    ) {
                        current.copy(isSaving = false, amountError = error.message)
                    } else {
                        current.copy(isSaving = false, error = error.displayMessage)
                    }
                }
            }
        }
    }

    private fun amountErrorMessage(reason: AmountParser.Reason): String = when (reason) {
        AmountParser.Reason.EMPTY -> "Enter an amount."
        AmountParser.Reason.NOT_A_NUMBER -> "That is not a number."
        AmountParser.Reason.TOO_MANY_DECIMAL_POINTS -> "Only one decimal point."
        AmountParser.Reason.TOO_MANY_DECIMAL_DIGITS ->
            if (_uiState.value.currency.decimalDigits == 0) {
                "${_uiState.value.currency.code} has no decimal places."
            } else {
                "At most ${_uiState.value.currency.decimalDigits} decimal places."
            }
        AmountParser.Reason.NOT_POSITIVE -> "Enter an amount greater than zero."
        AmountParser.Reason.TOO_LARGE -> "That amount is too large."
    }

    /** Renders a stored amount back into the raw text the field expects. */
    private fun amountToInput(amountMinor: Long, decimalDigits: Int): String {
        if (decimalDigits == 0) return amountMinor.toString()
        val scale = generateSequence(1L) { it * 10 }.elementAt(decimalDigits)
        val whole = amountMinor / scale
        val fraction = amountMinor % scale
        return "$whole.${fraction.toString().padStart(decimalDigits, '0')}"
    }

    companion object {
        fun defaultDate(): LocalDate = LocalDate.now()
    }
}
