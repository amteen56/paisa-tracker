package com.amteen.paisa.ui.screen.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.money.AmountParser
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.BudgetInput
import com.amteen.paisa.domain.usecase.GetBudgetHistoryUseCase
import com.amteen.paisa.domain.usecase.SaveBudgetUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Backs both Add Budget and Edit Budget.
 *
 * On an existing budget it also loads that budget's recent months, so the user can
 * see what the limit has actually meant in practice before changing it — a limit is
 * much easier to set well next to the last six months of the same figure.
 */
class BudgetEditViewModel(
    private val budgetId: String?,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    private val saveBudget: SaveBudgetUseCase,
    private val getBudgetHistory: GetBudgetHistoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetEditUiState(isEditing = budgetId != null))
    val uiState: StateFlow<BudgetEditUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            budgetRepository.load()

            val settings = settingsRepository.settings.value
            val table = CurrencyTable(currencyRepository.currencies.value, settings.baseCurrencyCode)

            val budget = budgetId?.let { budgetRepository.getById(it) }
            if (budgetId != null && budget == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "That budget no longer exists.")
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isEditing = budget != null,
                    categories = spendableCategories(budget?.categoryId),
                    currency = budget?.let { table.currency(it.currencyCode) } ?: table.base,
                    limitInput = budget?.let {
                        amountToInput(it.limitMinor, table.currency(it.currencyCode).decimalDigits)
                    } ?: "",
                    selectedCategoryId = budget?.categoryId,
                    selectedSubcategoryId = budget?.subcategoryId,
                    pinnedToMonth = budget?.period != null,
                    period = budget?.period ?: YearMonth.now(),
                )
            }

            if (budget != null) loadHistory(budget.id)
        }
    }

    /**
     * Categories a spending limit can be set on.
     *
     * Income-only categories are out — a budget on one would sit at 0% forever. An
     * archived category stays in only when the budget being edited already points at
     * it, so an old budget still shows what it is for; archiving is forgiven,
     * inapplicability is not. The same rule the transaction form uses.
     */
    private fun spendableCategories(selectedId: String?) =
        categoryRepository.categories.value.let { all ->
            val active = all.filter {
                !it.archived && it.applicableTo.allows(TransactionType.EXPENSE)
            }
            val selected = selectedId
                ?.let { id -> all.firstOrNull { it.id == id } }
                ?.takeIf { it.applicableTo.allows(TransactionType.EXPENSE) }
            if (selected != null && active.none { it.id == selected.id }) {
                active + selected
            } else {
                active
            }
        }

    private fun loadHistory(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(historyLoading = true) }
            val history = getBudgetHistory(id)
            _uiState.update { it.copy(historyLoading = false, history = history) }
        }
    }

    fun onEvent(event: BudgetEditEvent) {
        when (event) {
            is BudgetEditEvent.LimitChanged -> _uiState.update {
                it.copy(limitInput = event.input, limitError = null)
            }


            is BudgetEditEvent.CategorySelected -> _uiState.update {
                // Changing the category invalidates any subcategory chosen under the
                // old one.
                it.copy(
                    selectedCategoryId = event.id,
                    selectedSubcategoryId = null,
                    categoryError = null,
                )
            }

            is BudgetEditEvent.SubcategorySelected -> _uiState.update {
                // Tapping the selected chip again clears it, widening the budget back
                // out to the whole category.
                val next = if (it.selectedSubcategoryId == event.id) null else event.id
                it.copy(selectedSubcategoryId = next)
            }

            is BudgetEditEvent.RecurringChanged -> _uiState.update {
                it.copy(pinnedToMonth = !event.recurring, categoryError = null)
            }

            BudgetEditEvent.PreviousMonth -> _uiState.update {
                it.copy(period = it.period.minusMonths(1), categoryError = null)
            }

            BudgetEditEvent.NextMonth -> _uiState.update {
                it.copy(period = it.period.plusMonths(1), categoryError = null)
            }

            BudgetEditEvent.Save -> save()

            BudgetEditEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        // Budgets allow zero through the parser but the use case rejects it — a
        // limit of zero is either a typo or a way of saying "archive this", and both
        // are better handled explicitly.
        val parsed = AmountParser.parse(state.limitInput, state.currency)
        if (parsed !is AmountParser.Result.Valid) {
            _uiState.update {
                it.copy(
                    limitError = limitErrorMessage(
                        (parsed as AmountParser.Result.Invalid).reason,
                        state,
                    ),
                )
            }
            return
        }

        val categoryId = state.selectedCategoryId
        if (categoryId == null) {
            _uiState.update { it.copy(categoryError = "Choose a category.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, limitError = null, categoryError = null) }

        viewModelScope.launch {
            val result = saveBudget(
                BudgetInput(
                    id = budgetId,
                    categoryId = categoryId,
                    subcategoryId = state.selectedSubcategoryId,
                    limitMinor = parsed.amountMinor,
                    currencyCode = state.currency.code,
                    period = if (state.pinnedToMonth) state.period else null,
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
    private fun BudgetEditUiState.withError(error: AppError): BudgetEditUiState {
        if (error !is AppError.Validation) return copy(error = error.displayMessage)
        return when (error.field) {
            SaveBudgetUseCase.FIELD_LIMIT -> copy(limitError = error.message)
            SaveBudgetUseCase.FIELD_CATEGORY,
            SaveBudgetUseCase.FIELD_SUBCATEGORY,
            -> copy(categoryError = error.message)
            else -> copy(error = error.message)
        }
    }

    private fun limitErrorMessage(
        reason: AmountParser.Reason,
        state: BudgetEditUiState,
    ): String = when (reason) {
        AmountParser.Reason.EMPTY -> "Enter a limit."
        AmountParser.Reason.NOT_A_NUMBER -> "That is not a number."
        AmountParser.Reason.TOO_MANY_DECIMAL_POINTS -> "Only one decimal point."
        AmountParser.Reason.TOO_MANY_DECIMAL_DIGITS ->
            if (state.currency.decimalDigits == 0) {
                "${state.currency.code} has no decimal places."
            } else {
                "At most ${state.currency.decimalDigits} decimal places."
            }
        AmountParser.Reason.NOT_POSITIVE -> "Enter a limit greater than zero."
        AmountParser.Reason.TOO_LARGE -> "That limit is too large."
    }

    /** Renders a stored limit back into the raw text the field expects. */
    private fun amountToInput(amountMinor: Long, decimalDigits: Int): String {
        if (decimalDigits == 0) return amountMinor.toString()
        val scale = generateSequence(1L) { it * 10 }.elementAt(decimalDigits)
        return "${amountMinor / scale}." +
            (amountMinor % scale).toString().padStart(decimalDigits, '0')
    }
}
