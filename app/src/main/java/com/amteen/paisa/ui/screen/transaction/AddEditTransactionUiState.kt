package com.amteen.paisa.ui.screen.transaction

import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the add/edit form renders. One immutable object in, one event lambda
 * out — see CLAUDE.md.
 */
data class AddEditTransactionUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,

    val type: TransactionType = TransactionType.EXPENSE,

    /** Raw text, not a parsed amount: the field must render exactly what was typed. */
    val amountInput: String = "",
    val amountError: String? = null,

    /** Always PKR. Carried on the state because formatting needs its symbol. */
    val currency: Currency = CurrencyTable.fallback("PKR"),

    /** Already filtered to those valid for [type]. */
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedSubcategoryId: String? = null,

    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),

    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedPaymentMethodId: String? = null,

    val notes: String = "",
    val notesVisible: Boolean = false,

    val showDatePicker: Boolean = false,
    val showDeleteConfirm: Boolean = false,

    /** A blocking problem: the record could not be loaded, or a write failed. */
    val error: String? = null,

    /** Set once the write succeeds so the screen can pop. */
    val finished: Boolean = false,
) {
    val selectedCategory: Category?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    /**
     * The chips to offer. Archived subcategories stay out of the picker — except
     * the one this transaction already uses, which must remain visible and
     * selected. Dropping it would silently clear the user's choice while they were
     * editing something else entirely.
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

    val selectedSubcategory: Subcategory?
        get() = subcategories.firstOrNull { it.id == selectedSubcategoryId }

    /**
     * The Save affordance stays enabled even when incomplete, so tapping it can
     * *say what is missing*. A silently disabled button leaves the user tapping a
     * dead control with no idea why.
     */
    val canSave: Boolean
        get() = !isSaving && !isLoading

    val hasEnteredAmount: Boolean
        get() = amountInput.isNotBlank()
}

/**
 * Everything the form can do. A sealed interface rather than a bag of lambdas so
 * the screen has exactly one output and adding an action cannot be half-wired.
 */
sealed interface AddEditTransactionEvent {
    data class TypeChanged(val type: TransactionType) : AddEditTransactionEvent
    data class AmountChanged(val input: String) : AddEditTransactionEvent
    data class CategorySelected(val id: String) : AddEditTransactionEvent
    data class SubcategorySelected(val id: String?) : AddEditTransactionEvent
    data class DescriptionChanged(val text: String) : AddEditTransactionEvent
    data class DateSelected(val date: LocalDate) : AddEditTransactionEvent
    data class PaymentMethodSelected(val id: String?) : AddEditTransactionEvent
    data class NotesChanged(val text: String) : AddEditTransactionEvent

    data object ToggleNotes : AddEditTransactionEvent
    data object OpenDatePicker : AddEditTransactionEvent
    data object DismissDatePicker : AddEditTransactionEvent
    data object Save : AddEditTransactionEvent
    data object DismissError : AddEditTransactionEvent
}
