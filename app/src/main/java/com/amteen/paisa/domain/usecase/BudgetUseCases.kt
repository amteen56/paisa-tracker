package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.repository.BudgetAlertStateRepository
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import java.time.YearMonth
import java.util.UUID

/**
 * What the budget form produces. [id] null means insert.
 *
 * [period] null means the budget recurs every month; a value pins it to that one
 * month.
 */
data class BudgetInput(
    val id: String? = null,
    val categoryId: String,
    val subcategoryId: String? = null,
    val limitMinor: Long,
    val currencyCode: String,
    val period: YearMonth? = null,
)

/**
 * Validates and persists a budget.
 *
 * The interesting validation is the duplicate check. Two live budgets over the same
 * category and period would both be counted, both be shown, and both fire their own
 * alerts for the same spending — the user would see one overspend reported twice and
 * have no way to tell which limit was real.
 */
class SaveBudgetUseCase(
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend operator fun invoke(input: BudgetInput): AppResult<Budget> {
        validate(input)?.let { return AppResult.Err(it) }

        val existing = input.id?.let { budgets.getById(it) }
        val budget = Budget(
            id = existing?.id ?: input.id ?: newId(),
            categoryId = input.categoryId,
            subcategoryId = input.subcategoryId,
            limitMinor = input.limitMinor,
            currencyCode = input.currencyCode,
            period = input.period,
            // Saving an archived budget brings it back: the user is editing it, so
            // they clearly want it in force again.
            archived = false,
        )

        return try {
            budgets.upsert(budget)
            AppResult.Ok(budget)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage(
                    message = "Could not save the budget. Your existing data is unchanged.",
                    cause = e,
                ),
            )
        }
    }

    private suspend fun validate(input: BudgetInput): AppError.Validation? {
        if (input.limitMinor <= 0L) {
            return AppError.Validation(FIELD_LIMIT, "Enter a limit greater than zero.")
        }
        if (input.currencyCode.isBlank()) {
            return AppError.Validation(FIELD_CURRENCY, "Choose a currency.")
        }
        if (input.categoryId.isBlank()) {
            return AppError.Validation(FIELD_CATEGORY, "Choose a category.")
        }

        val category = categories.getById(input.categoryId)
            ?: return AppError.Validation(FIELD_CATEGORY, "That category no longer exists.")

        // A budget is a spending limit, so it only means anything on a category that
        // expenses can be filed under. An income-only category would sit at 0% forever.
        if (!category.applicableTo.allows(TransactionType.EXPENSE)) {
            return AppError.Validation(
                FIELD_CATEGORY,
                "${category.name} is an income category, so it cannot have a spending limit.",
            )
        }

        if (input.subcategoryId != null && category.subcategory(input.subcategoryId) == null) {
            return AppError.Validation(
                FIELD_SUBCATEGORY,
                "That subcategory is not part of ${category.name}.",
            )
        }

        duplicateOf(input)?.let { clash ->
            val scope = if (clash.isRecurring) "every month" else "that month"
            return AppError.Validation(
                FIELD_CATEGORY,
                "There is already a budget for this category $scope. " +
                    "Edit that one instead of adding a second.",
            )
        }

        return null
    }

    /**
     * Another live budget covering exactly the same thing.
     *
     * Archived budgets are ignored, so a user can re-create a limit they retired
     * without first having to find and delete it.
     */
    private fun duplicateOf(input: BudgetInput): Budget? = budgets.budgets.value.firstOrNull {
        it.id != input.id &&
            !it.archived &&
            it.categoryId == input.categoryId &&
            it.subcategoryId == input.subcategoryId &&
            it.period == input.period
    }

    companion object {
        const val FIELD_LIMIT = "limit"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_CATEGORY = "category"
        const val FIELD_SUBCATEGORY = "subcategory"
    }
}

/**
 * Archives a budget: it stops counting, stops alerting and leaves the pickers, but
 * its past months still render correctly in history.
 */
class ArchiveBudgetUseCase(private val budgets: BudgetRepository) {
    suspend operator fun invoke(budgetId: String, archived: Boolean = true): AppResult<Unit> = try {
        budgets.archive(budgetId, archived)
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not update the budget.", e))
    }
}

/**
 * Deletes a budget outright.
 *
 * Unlike a category or a payment method, a budget is never referenced by a
 * transaction — usage is derived from the ledger, not recorded against the limit —
 * so there is nothing to orphan and no reference count to check. See CLAUDE.md rule
 * 4, which lists the types that *are* reference-counted; budgets are deliberately
 * not among them. Archive is still offered for anyone who wants the record kept.
 */
class DeleteBudgetUseCase(
    private val budgets: BudgetRepository,
    private val alerts: BudgetAlertStateRepository,
) {
    suspend operator fun invoke(budgetId: String): AppResult<Unit> = try {
        budgets.hardDelete(budgetId)
        // Otherwise a new budget that happened to reuse the id would inherit a
        // "already alerted" record it never earned.
        alerts.forget(budgetId)
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not delete the budget.", e))
    }
}
