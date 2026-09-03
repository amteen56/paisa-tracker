package com.amteen.paisa.ui.screen.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.usecase.ArchiveCategoryUseCase
import com.amteen.paisa.domain.usecase.CountCategoryReferencesUseCase
import com.amteen.paisa.domain.usecase.DeleteCategoryUseCase
import com.amteen.paisa.domain.usecase.RemovalOutcome
import com.amteen.paisa.domain.usecase.ReorderCategoriesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The category list, split into Expense and Income tabs.
 *
 * Everything visible is derived from [CategoryRepository.categories] rather than
 * copied into local state, so an edit made on the editor screen shows up here
 * without a refresh. The only local state is what the *view* owns: which tab, and
 * whether archived rows are shown.
 *
 * [pendingOrder] is the one exception. A drag has to redraw on every frame, and a
 * file write per swap would be both wasteful and visibly laggy — so the new order
 * is held here, rendered optimistically, and written once when the finger lifts.
 */
class CategoryListViewModel(
    private val categoryRepository: CategoryRepository,
    private val countReferences: CountCategoryReferencesUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val reorderCategories: ReorderCategoriesUseCase,
) : ViewModel() {

    private data class Chrome(
        val isLoading: Boolean = true,
        val showArchived: Boolean = false,
        val pendingRemoval: PendingRemoval? = null,
        val message: String? = null,
        val error: String? = null,
    )

    private val selectedScope = MutableStateFlow(CategoryScope.EXPENSE)
    private val pendingOrder = MutableStateFlow<List<String>?>(null)
    private val chrome = MutableStateFlow(Chrome())

    val uiState: StateFlow<CategoryListUiState> = combine(
        categoryRepository.categories,
        selectedScope,
        pendingOrder,
        chrome,
    ) { categories, scope, order, ui ->
        val visible = categories.filter { it.applicableTo.overlaps(scope) }
        val active = visible.filterNot { it.archived }.applyOrder(order)

        CategoryListUiState(
            isLoading = ui.isLoading,
            scope = scope,
            active = active.map { it.toRow() },
            archived = visible.filter { it.archived }.map { it.toRow() },
            showArchived = ui.showArchived,
            pendingRemoval = ui.pendingRemoval,
            message = ui.message,
            error = ui.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryListUiState(),
    )

    init {
        viewModelScope.launch {
            categoryRepository.load()
            chrome.update { it.copy(isLoading = false) }
        }
    }

    fun onEvent(event: CategoryListEvent) {
        when (event) {
            is CategoryListEvent.ScopeSelected -> {
                // The pending order is a list of ids from the old tab; carrying it
                // across would reorder rows the user never touched.
                pendingOrder.value = null
                selectedScope.value = event.scope
            }

            CategoryListEvent.ToggleArchivedVisible -> chrome.update {
                it.copy(showArchived = !it.showArchived)
            }

            is CategoryListEvent.RemoveRequested -> requestRemoval(event.id)

            CategoryListEvent.RemoveConfirmed -> confirmRemoval()

            CategoryListEvent.RemoveDismissed -> chrome.update { it.copy(pendingRemoval = null) }

            is CategoryListEvent.ArchiveToggled -> setArchived(event.id, event.archived)

            is CategoryListEvent.Moved -> move(event.fromId, event.toId)

            CategoryListEvent.OrderCommitted -> commitOrder()

            is CategoryListEvent.MoveStep -> moveStep(event.id, event.up)

            CategoryListEvent.MessageShown -> chrome.update { it.copy(message = null) }

            CategoryListEvent.DismissError -> chrome.update { it.copy(error = null) }
        }
    }

    // -- Removal ------------------------------------------------------------

    private fun requestRemoval(id: String) {
        val category = uiState.value.let { state ->
            (state.active + state.archived).firstOrNull { it.id == id }
        } ?: return

        viewModelScope.launch {
            val references = countReferences(id)
            chrome.update {
                it.copy(
                    pendingRemoval = PendingRemoval(
                        id = id,
                        name = category.name,
                        references = references,
                    ),
                )
            }
        }
    }

    private fun confirmRemoval() {
        val pending = chrome.value.pendingRemoval ?: return
        chrome.update { it.copy(pendingRemoval = null) }

        viewModelScope.launch {
            // Archive is the outcome whenever anything still points at it; the use
            // case re-checks rather than trusting the count the dialog was built on,
            // because a transaction may have been added since it opened.
            val result = if (pending.canDelete) {
                deleteCategory(pending.id)
            } else {
                archiveCategory(pending.id, archived = true)
            }

            when (result) {
                is AppResult.Err -> chrome.update { it.copy(error = result.error.displayMessage) }
                is AppResult.Ok -> chrome.update {
                    it.copy(message = describe(result.value, pending.name))
                }
            }
        }
    }

    private fun describe(outcome: RemovalOutcome, name: String): String = when (outcome) {
        RemovalOutcome.Deleted -> "“$name” deleted."
        RemovalOutcome.Archived -> "“$name” archived. It still appears in your history."
        is RemovalOutcome.Blocked ->
            "“$name” is used by ${outcome.references.describe()}, so it was archived instead."
    }

    private fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            when (val result = archiveCategory(id, archived)) {
                is AppResult.Err -> chrome.update { it.copy(error = result.error.displayMessage) }
                is AppResult.Ok -> chrome.update {
                    it.copy(message = if (archived) "Archived." else "Restored.")
                }
            }
        }
    }

    // -- Reordering ---------------------------------------------------------

    private fun move(fromId: String, toId: String) {
        if (fromId == toId) return
        val current = uiState.value.active.map { it.id }
        val from = current.indexOf(fromId)
        val to = current.indexOf(toId)
        if (from < 0 || to < 0) return

        pendingOrder.value = current.toMutableList().apply {
            add(to, removeAt(from))
        }
    }

    private fun commitOrder() {
        val order = pendingOrder.value ?: return
        persist(order)
    }

    /**
     * One step up or down, for screen-reader and keyboard users — a long-press drag
     * is not operable with TalkBack on, so it cannot be the only way to reorder.
     */
    private fun moveStep(id: String, up: Boolean) {
        val current = uiState.value.active.map { it.id }
        val index = current.indexOf(id)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in current.indices) return

        val reordered = current.toMutableList().apply {
            add(target, removeAt(index))
        }
        pendingOrder.value = reordered
        persist(reordered)
    }

    private fun persist(visibleOrder: List<String>) {
        viewModelScope.launch {
            when (val result = reorderCategories(fullOrderWith(visibleOrder))) {
                is AppResult.Err -> {
                    // Drop the optimistic order so the list snaps back to what is
                    // actually on disk, rather than showing an order that was lost.
                    pendingOrder.value = null
                    chrome.update { it.copy(error = result.error.displayMessage) }
                }
                is AppResult.Ok -> pendingOrder.value = null
            }
        }
    }

    /**
     * Splices [visibleOrder] back into the full list.
     *
     * The user reordered one tab, but `sortOrder` is global. Rewriting only the
     * visible ids would leave the hidden ones holding positions that collide with
     * the new ones, so the ids the user *can* see are rewritten into exactly the
     * slots they already occupied and everything else keeps its place.
     */
    private fun fullOrderWith(visibleOrder: List<String>): List<String> {
        val all = categoryRepository.categories.value
        val visible = visibleOrder.toSet()
        val queue = ArrayDeque(visibleOrder)
        return all.map { category ->
            if (category.id in visible && queue.isNotEmpty()) queue.removeFirst() else category.id
        }
    }

    private fun List<Category>.applyOrder(order: List<String>?): List<Category> {
        if (order == null) return this
        val position = order.withIndex().associate { (index, id) -> id to index }
        return sortedBy { position[it.id] ?: Int.MAX_VALUE }
    }

    private fun Category.toRow() = CategoryRowUi(
        id = id,
        name = name,
        iconKey = iconKey,
        colorArgb = colorArgb,
        subcategoryCount = activeSubcategories.size,
        archived = archived,
        sharedAcrossTypes = applicableTo == CategoryScope.BOTH,
    )
}
