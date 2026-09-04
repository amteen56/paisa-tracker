package com.amteen.paisa.ui.screen.budget

import com.amteen.paisa.domain.model.BudgetProgress
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.Subcategory
import java.time.YearMonth

/**
 * The budget form.
 *
 * [limitInput] is raw text, not a parsed amount: a form in progress is allowed to
 * hold "1,2" while the user is still typing. Parsing happens once, on save, through
 * `AmountParser`.
 */
data class BudgetEditUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val finished: Boolean = false,

    val limitInput: String = "",
    val limitError: String? = null,

    val currency: Currency = CurrencyTable.fallback("PKR"),

    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedSubcategoryId: String? = null,
    val categoryError: String? = null,

    /** False means the budget recurs every month; true pins it to [period]. */
    val pinnedToMonth: Boolean = false,
    val period: YearMonth = YearMonth.now(),

    /** Past months, newest first. Empty for a budget that has not been saved yet. */
    val history: List<BudgetProgress> = emptyList(),
    val historyLoading: Boolean = false,

    val error: String? = null,
) {
    val selectedCategory: Category?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    /**
     * Subcategories offered under the chosen category.
     *
     * The currently-selected one is re-admitted even when archived, for the same
     * reason as the transaction form: otherwise editing an older budget shows a chip
     * row with nothing selected and the selection looks lost when it is not.
     */
    val subcategories: List<Subcategory>
        get() {
            val category = selectedCategory ?: return emptyList()
            val active = category.activeSubcategories
            val selected = category.subcategory(selectedSubcategoryId)
            return if (selected != null && active.none { it.id == selected.id }) {
                active + selected
            } else {
                active
            }
        }

    val canSave: Boolean get() = !isSaving && selectedCategoryId != null && limitInput.isNotBlank()
}

sealed interface BudgetEditEvent {
    data class LimitChanged(val input: String) : BudgetEditEvent
    data class CategorySelected(val id: String) : BudgetEditEvent
    data class SubcategorySelected(val id: String) : BudgetEditEvent
    data class RecurringChanged(val recurring: Boolean) : BudgetEditEvent
    data object PreviousMonth : BudgetEditEvent
    data object NextMonth : BudgetEditEvent
    data object Save : BudgetEditEvent
    data object DismissError : BudgetEditEvent
}
