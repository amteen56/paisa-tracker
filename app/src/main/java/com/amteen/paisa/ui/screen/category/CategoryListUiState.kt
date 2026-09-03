package com.amteen.paisa.ui.screen.category

import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.usecase.ReferenceCount

/** One row of the category list, flattened for rendering. */
data class CategoryRowUi(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val subcategoryCount: Int,
    val archived: Boolean,
    /** True when the category also applies to the tab the user is *not* on. */
    val sharedAcrossTypes: Boolean,
)

/**
 * The confirmation in flight.
 *
 * [references] is counted *before* the dialog opens rather than after the user
 * confirms, so the dialog can offer the action that will actually work: Delete when
 * nothing points at the category, Archive when something does. Asking "Delete?" and
 * then refusing would be a worse experience than never offering it.
 */
data class PendingRemoval(
    val id: String,
    val name: String,
    val references: ReferenceCount,
) {
    val canDelete: Boolean get() = !references.isReferenced
}

data class CategoryListUiState(
    val isLoading: Boolean = true,
    /** Which tab is showing. Only EXPENSE and INCOME are ever set here. */
    val scope: CategoryScope = CategoryScope.EXPENSE,
    val active: List<CategoryRowUi> = emptyList(),
    val archived: List<CategoryRowUi> = emptyList(),
    val showArchived: Boolean = false,
    val pendingRemoval: PendingRemoval? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && archived.isEmpty()
}

sealed interface CategoryListEvent {
    data class ScopeSelected(val scope: CategoryScope) : CategoryListEvent
    data object ToggleArchivedVisible : CategoryListEvent

    /** Opens the remove dialog after counting what points at the category. */
    data class RemoveRequested(val id: String) : CategoryListEvent
    data object RemoveConfirmed : CategoryListEvent
    data object RemoveDismissed : CategoryListEvent

    data class ArchiveToggled(val id: String, val archived: Boolean) : CategoryListEvent

    /** A drag moved [fromId] to where [toId] sits. Optimistic; not yet written. */
    data class Moved(val fromId: String, val toId: String) : CategoryListEvent

    /** The drag ended — persist whatever order the list is in now. */
    data object OrderCommitted : CategoryListEvent

    /** Accessible reordering, for when a drag gesture is not available. */
    data class MoveStep(val id: String, val up: Boolean) : CategoryListEvent

    data object MessageShown : CategoryListEvent
    data object DismissError : CategoryListEvent
}
