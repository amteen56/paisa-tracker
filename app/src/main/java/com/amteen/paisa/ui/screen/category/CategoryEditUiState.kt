package com.amteen.paisa.ui.screen.category

import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.ui.theme.CategoryPalette

/**
 * One editable subcategory row. [id] null means the user just added it and it has
 * no identity on disk yet.
 */
data class SubcategoryRowUi(
    val id: String? = null,
    val name: String = "",
    val error: String? = null,
)

/** An archived subcategory, shown read-only with the option to bring it back. */
data class ArchivedSubcategoryUi(
    val id: String,
    val name: String,
)

data class CategoryEditUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val name: String = "",
    val nameError: String? = null,
    val applicableTo: CategoryScope = CategoryScope.EXPENSE,
    val iconKey: String = "category",
    val colorArgb: Int = CategoryPalette.default,
    val subcategories: List<SubcategoryRowUi> = emptyList(),
    /**
     * Subcategories kept only because transactions still point at them. Listed so
     * the user can see *why* something they removed is still around, and undo it.
     */
    val archivedSubcategories: List<ArchivedSubcategoryUi> = emptyList(),
    val isSaving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !isSaving && !isLoading && name.isNotBlank()
}

sealed interface CategoryEditEvent {
    data class NameChanged(val text: String) : CategoryEditEvent
    data class ScopeChanged(val scope: CategoryScope) : CategoryEditEvent
    data class IconSelected(val key: String) : CategoryEditEvent
    data class ColorSelected(val argb: Int) : CategoryEditEvent

    data object SubcategoryAdded : CategoryEditEvent
    data class SubcategoryChanged(val index: Int, val text: String) : CategoryEditEvent
    data class SubcategoryRemoved(val index: Int) : CategoryEditEvent

    /** Moves an archived subcategory back into the editable list. */
    data class SubcategoryRestored(val id: String) : CategoryEditEvent

    data object Save : CategoryEditEvent
    data object DismissError : CategoryEditEvent
}
