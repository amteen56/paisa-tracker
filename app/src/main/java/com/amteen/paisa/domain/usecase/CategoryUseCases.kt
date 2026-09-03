package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import java.util.UUID

/**
 * One row of the category editor. [id] null means the user just added it.
 *
 * Separate from [Subcategory] because a half-typed row is not yet a subcategory —
 * it has no id and its name may be blank.
 */
data class SubcategoryInput(
    val id: String? = null,
    val name: String,
    /**
     * Carried explicitly rather than inherited from what is on disk, so the editor
     * can restore an archived subcategory simply by submitting it as a live row.
     */
    val archived: Boolean = false,
)

/** What the category editor produces. [id] null means insert. */
data class CategoryInput(
    val id: String? = null,
    val name: String,
    val applicableTo: CategoryScope,
    val iconKey: String,
    val colorArgb: Int,
    val subcategories: List<SubcategoryInput> = emptyList(),
)

/**
 * Validates and persists a category, reconciling its subcategories.
 *
 * The subtle part is **removal**. A subcategory the user deleted in the editor
 * cannot simply be dropped: if any transaction still points at it, that transaction
 * would render a blank where its subcategory used to be. So a referenced
 * subcategory is archived instead — it leaves the picker but keeps resolving.
 * Unreferenced ones are genuinely removed. Same rule as categories, one level down.
 */
class SaveCategoryUseCase(
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend operator fun invoke(input: CategoryInput): AppResult<Category> {
        val existing = input.id?.let { categories.getById(it) }

        validate(input, existing)?.let { return AppResult.Err(it) }

        val subcategories = try {
            reconcileSubcategories(input, existing)
        } catch (e: Exception) {
            return AppResult.Err(
                AppError.Storage("Could not read the existing subcategories.", e),
            )
        }

        val category = Category(
            id = existing?.id ?: input.id ?: newId(),
            name = input.name.trim(),
            applicableTo = input.applicableTo,
            iconKey = input.iconKey.ifBlank { DEFAULT_ICON },
            colorArgb = input.colorArgb,
            // A new category is appended; the repository assigns the position.
            sortOrder = existing?.sortOrder ?: 0,
            archived = existing?.archived ?: false,
            subcategories = subcategories,
        )

        return try {
            categories.upsert(category)
            AppResult.Ok(category)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage(
                    message = "Could not save the category. Your existing data is unchanged.",
                    cause = e,
                ),
            )
        }
    }

    /**
     * Merges the editor's rows with what is already on disk.
     *
     * Order comes from the editor. Archived-but-referenced leftovers are appended
     * after the live rows, so they never interleave with what the user sees.
     */
    private suspend fun reconcileSubcategories(
        input: CategoryInput,
        existing: Category?,
    ): List<Subcategory> {
        val previous = existing?.subcategories.orEmpty().associateBy { it.id }

        val kept = input.subcategories.mapIndexed { index, row ->
            Subcategory(
                // Keeping the id is what makes an edit an edit: change it and every
                // transaction pointing at this subcategory is orphaned.
                id = row.id ?: newId(),
                name = row.name.trim(),
                sortOrder = index,
                archived = row.archived,
            )
        }

        val keptIds = kept.mapTo(mutableSetOf()) { it.id }
        val removed = previous.values.filterNot { it.id in keptIds }

        // Only the ones something still points at survive, and only as archived.
        val orphanedButReferenced = removed
            .filter { transactions.countBySubcategory(it.id) > 0 }
            .sortedBy { it.sortOrder }
            .mapIndexed { index, sub ->
                sub.copy(sortOrder = kept.size + index, archived = true)
            }

        return kept + orphanedButReferenced
    }

    private fun validate(input: CategoryInput, existing: Category?): AppError.Validation? {
        val name = input.name.trim()
        if (name.isEmpty()) {
            return AppError.Validation(FIELD_NAME, "Give the category a name.")
        }
        if (name.length > MAX_NAME) {
            return AppError.Validation(FIELD_NAME, "Keep the name under $MAX_NAME characters.")
        }

        val clash = categories.categories.value
            .firstOrNull { it.id != existing?.id && it.name.equals(name, ignoreCase = true) }
        if (clash != null) {
            // Point at the archived one rather than refusing flatly — otherwise the
            // user retypes the same name and gets the same rejection.
            return AppError.Validation(
                FIELD_NAME,
                if (clash.archived) {
                    "“${clash.name}” already exists but is archived. " +
                        "Restore it instead of creating a duplicate."
                } else {
                    "“${clash.name}” already exists."
                },
            )
        }

        input.subcategories.forEachIndexed { index, row ->
            val subName = row.name.trim()
            if (subName.isEmpty()) {
                return AppError.Validation(
                    subcategoryField(index),
                    "Name this subcategory or remove the row.",
                )
            }
            if (subName.length > MAX_NAME) {
                return AppError.Validation(
                    subcategoryField(index),
                    "Keep the name under $MAX_NAME characters.",
                )
            }
            val duplicate = input.subcategories
                .take(index)
                .any { it.name.trim().equals(subName, ignoreCase = true) }
            if (duplicate) {
                return AppError.Validation(
                    subcategoryField(index),
                    "“$subName” is already a subcategory here.",
                )
            }
        }

        return null
    }

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_SUBCATEGORY_PREFIX = "subcategory:"

        const val MAX_NAME = 40
        const val DEFAULT_ICON = "category"

        /** Field key for the subcategory row at [index], so the editor can point at it. */
        fun subcategoryField(index: Int): String = "$FIELD_SUBCATEGORY_PREFIX$index"

        /** The row index a [FIELD_SUBCATEGORY_PREFIX] field refers to, if it is one. */
        fun subcategoryIndex(field: String?): Int? =
            field?.removePrefix(FIELD_SUBCATEGORY_PREFIX)
                ?.takeIf { field.startsWith(FIELD_SUBCATEGORY_PREFIX) }
                ?.toIntOrNull()
    }
}

/**
 * Counts everything pointing at a category, so the UI can decide between Delete and
 * Archive before it offers either.
 */
class CountCategoryReferencesUseCase(
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
) {
    suspend operator fun invoke(categoryId: String): ReferenceCount = ReferenceCount(
        transactions = transactions.countByCategory(categoryId),
        budgets = budgets.budgets.value.count { it.categoryId == categoryId },
    )
}

/**
 * Removes a category **only** at reference count zero; otherwise reports what is in
 * the way and writes nothing. See CLAUDE.md rule 4.
 */
class DeleteCategoryUseCase(
    private val categories: CategoryRepository,
    private val countReferences: CountCategoryReferencesUseCase,
) {
    suspend operator fun invoke(categoryId: String): AppResult<RemovalOutcome> = try {
        val references = countReferences(categoryId)
        if (references.isReferenced) {
            AppResult.Ok(RemovalOutcome.Blocked(references))
        } else {
            categories.hardDelete(categoryId)
            AppResult.Ok(RemovalOutcome.Deleted)
        }
    } catch (e: Exception) {
        AppResult.Err(
            AppError.Storage("Could not delete the category. Nothing was changed.", e),
        )
    }
}

/** Hides a category from pickers while leaving history and reports intact. */
class ArchiveCategoryUseCase(private val categories: CategoryRepository) {
    suspend operator fun invoke(categoryId: String, archived: Boolean): AppResult<RemovalOutcome> =
        try {
            categories.archive(categoryId, archived)
            AppResult.Ok(if (archived) RemovalOutcome.Archived else RemovalOutcome.Deleted)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage("Could not update the category. Nothing was changed.", e),
            )
        }
}

/** Persists a new display order. [orderedIds] is the full list, top to bottom. */
class ReorderCategoriesUseCase(private val categories: CategoryRepository) {
    suspend operator fun invoke(orderedIds: List<String>): AppResult<Unit> = try {
        categories.reorder(orderedIds)
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not save the new order.", e))
    }
}
