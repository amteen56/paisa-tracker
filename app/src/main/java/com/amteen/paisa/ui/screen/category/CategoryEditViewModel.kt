package com.amteen.paisa.ui.screen.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.usecase.CategoryInput
import com.amteen.paisa.domain.usecase.SaveCategoryUseCase
import com.amteen.paisa.domain.usecase.SubcategoryInput
import com.amteen.paisa.ui.theme.CategoryPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs both Add Category and Edit Category — as with the transaction form, one
 * implementation so the two cannot drift apart.
 *
 * Subcategories are edited as a plain list of rows rather than saved one at a time.
 * A subcategory has no meaning apart from its parent, so committing the whole
 * category at once keeps the file write atomic: there is no state in which half the
 * user's renames landed.
 */
class CategoryEditViewModel(
    private val categoryId: String?,
    initialScope: CategoryScope,
    private val categoryRepository: CategoryRepository,
    private val saveCategory: SaveCategoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CategoryEditUiState(
            isEditing = categoryId != null,
            applicableTo = initialScope,
        ),
    )
    val uiState: StateFlow<CategoryEditUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            categoryRepository.load()

            if (categoryId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        colorArgb = CategoryPalette.default,
                    )
                }
                return@launch
            }

            val category = categoryRepository.getById(categoryId)
            if (category == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "That category no longer exists.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditing = true,
                    name = category.name,
                    applicableTo = category.applicableTo,
                    iconKey = category.iconKey,
                    colorArgb = category.colorArgb,
                    subcategories = category.subcategories
                        .filterNot { sub -> sub.archived }
                        .sortedBy { sub -> sub.sortOrder }
                        .map { sub -> SubcategoryRowUi(id = sub.id, name = sub.name) },
                    archivedSubcategories = category.subcategories
                        .filter { sub -> sub.archived }
                        .sortedBy { sub -> sub.sortOrder }
                        .map { sub -> ArchivedSubcategoryUi(id = sub.id, name = sub.name) },
                )
            }
        }
    }

    fun onEvent(event: CategoryEditEvent) {
        when (event) {
            is CategoryEditEvent.NameChanged -> _uiState.update {
                it.copy(name = event.text, nameError = null)
            }

            is CategoryEditEvent.ScopeChanged -> _uiState.update {
                it.copy(applicableTo = event.scope)
            }

            is CategoryEditEvent.IconSelected -> _uiState.update { it.copy(iconKey = event.key) }

            is CategoryEditEvent.ColorSelected -> _uiState.update { it.copy(colorArgb = event.argb) }

            CategoryEditEvent.SubcategoryAdded -> _uiState.update {
                it.copy(subcategories = it.subcategories + SubcategoryRowUi())
            }

            is CategoryEditEvent.SubcategoryChanged -> _uiState.update { state ->
                state.copy(
                    subcategories = state.subcategories.mapIndexed { index, row ->
                        if (index == event.index) row.copy(name = event.text, error = null) else row
                    },
                )
            }

            is CategoryEditEvent.SubcategoryRemoved -> _uiState.update { state ->
                // Removing a row here is only an intent. Whether it is really
                // deleted or quietly archived depends on whether anything points at
                // it, and only SaveCategoryUseCase can answer that.
                state.copy(
                    subcategories = state.subcategories.filterIndexed { index, _ ->
                        index != event.index
                    },
                )
            }

            is CategoryEditEvent.SubcategoryRestored -> _uiState.update { state ->
                val restored = state.archivedSubcategories.firstOrNull { it.id == event.id }
                    ?: return@update state
                state.copy(
                    subcategories = state.subcategories +
                        SubcategoryRowUi(id = restored.id, name = restored.name),
                    archivedSubcategories = state.archivedSubcategories - restored,
                )
            }

            CategoryEditEvent.Save -> save()

            CategoryEditEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true, nameError = null) }

        viewModelScope.launch {
            val result = saveCategory(
                CategoryInput(
                    id = categoryId,
                    name = state.name,
                    applicableTo = state.applicableTo,
                    iconKey = state.iconKey,
                    colorArgb = state.colorArgb,
                    subcategories = state.subcategories.map {
                        SubcategoryInput(id = it.id, name = it.name, archived = false)
                    },
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
    private fun CategoryEditUiState.withError(error: AppError): CategoryEditUiState {
        if (error !is AppError.Validation) return copy(error = error.displayMessage)

        if (error.field == SaveCategoryUseCase.FIELD_NAME) {
            return copy(nameError = error.message)
        }

        val index = SaveCategoryUseCase.subcategoryIndex(error.field)
        if (index != null && index in subcategories.indices) {
            return copy(
                subcategories = subcategories.mapIndexed { i, row ->
                    if (i == index) row.copy(error = error.message) else row
                },
            )
        }

        return copy(error = error.message)
    }
}
