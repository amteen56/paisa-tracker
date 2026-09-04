package com.amteen.paisa.domain.usecase

import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetProgress
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.Transaction
import java.time.YearMonth

/**
 * How a list of budgets is ordered.
 *
 * The dashboard strip used to force [AT_RISK] on the grounds that it is a warning
 * surface and the budget in trouble must not be the one cut off. Budgets are now
 * user-orderable, and the user's arrangement wins by default — priority is something
 * only they can express, and an order that rearranges itself as spending moves is
 * disorienting on a screen you look at every day.
 *
 * [AT_RISK] is kept because the argument for it was a real one, and switching the
 * strip back is a one-word change.
 */
enum class BudgetOrder {
    /** The order the user dragged them into. Ties fall back to the label. */
    USER,

    /** Closest to its limit first. */
    AT_RISK;

    internal val comparator: Comparator<BudgetSummary>
        get() = when (this) {
            USER -> compareBy<BudgetSummary> { it.progress.budget.sortOrder }
                .thenBy { it.label }
            AT_RISK -> compareByDescending<BudgetSummary> { it.progress.percent }
                .thenBy { it.label }
        }
}

/**
 * A budget with its derived usage and the names needed to render it.
 *
 * [category] is nullable for the same reason it is on `TransactionDetails`: a budget
 * must keep rendering even if its category stopped resolving, rather than taking the
 * whole strip down with it.
 */
data class BudgetSummary(
    val progress: BudgetProgress,
    val category: Category?,
    val subcategory: Subcategory?,
    /**
     * The budget's own currency, resolved here rather than by the screen. Formatting
     * needs the real symbol and decimal digits — a code alone would render a rupee
     * limit as "PKR 3,000.00" and a yen one with two decimal places it does not have.
     */
    val currency: Currency,
) {
    val id: String get() = progress.budget.id

    /** "Food" for a category budget, "Food · Fast Food" for a subcategory one. */
    val label: String
        get() {
            val categoryName = category?.name ?: "Uncategorised"
            return subcategory?.let { "$categoryName · ${it.name}" } ?: categoryName
        }
}

/**
 * Budget usage, computed from transactions rather than stored.
 *
 * A running total on disk is how budget figures drift out of step with the ledger
 * that produced them, so there is none — see CLAUDE.md rule 6.
 *
 * Every contributing expense is converted into the **budget's own currency** before
 * being summed. A $50 dinner against a Rs. 20,000 grocery budget is 14,000 rupees of
 * it, not 50 — comparing the bare numbers would under-report by a factor of 280. See
 * CLAUDE.md rule 7.
 *
 * This takes its inputs as plain lists rather than repositories: the dashboard has
 * already loaded the month it needs, and re-reading the same shard through a second
 * flow would be both wasteful and a chance for the two figures to disagree.
 */
class GetBudgetStatusUseCase {

    operator fun invoke(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        month: YearMonth,
        table: CurrencyTable,
        categories: List<Category> = emptyList(),
        order: BudgetOrder = BudgetOrder.USER,
    ): List<BudgetSummary> {
        val applicable = budgets.filter { it.appliesTo(month) }
        if (applicable.isEmpty()) return emptyList()

        val categoryById = categories.associateBy { it.id }
        // Income never counts against a spending limit, and neither does an expense
        // from another month — a recurring budget is a *monthly* allowance.
        val monthExpenses = transactions.filter {
            it.type.isExpense && YearMonth.from(it.date) == month
        }

        return applicable
            .map { budget ->
                val category = categoryById[budget.categoryId]
                BudgetSummary(
                    progress = progressFor(budget, monthExpenses, month, table),
                    category = category,
                    subcategory = category?.subcategory(budget.subcategoryId),
                    currency = table.currency(budget.currencyCode),
                )
            }
            .sortedWith(order.comparator)
    }

    /**
     * One budget's usage for one month, with no regard for whether the budget
     * currently *applies* to that month.
     *
     * [invoke] filters by [Budget.appliesTo] because the dashboard should only show
     * live budgets. History deliberately does not: an archived budget, or one pinned
     * to a single month, still has a real figure for the months it was in force, and
     * refusing to compute it would leave the history screen blank for exactly the
     * budgets a user is most likely to be looking back at.
     *
     * [transactions] may span any range; only [month]'s expenses are counted.
     */
    fun progressFor(
        budget: Budget,
        transactions: List<Transaction>,
        month: YearMonth,
        table: CurrencyTable,
    ): BudgetProgress {
        var spent = 0L
        for (record in transactions) {
            if (!record.type.isExpense) continue
            if (YearMonth.from(record.date) != month) continue
            if (!budget.covers(record)) continue
            spent += table.convert(record.money, budget.currencyCode).amountMinor
        }
        return BudgetProgress(
            budget = budget,
            month = month,
            spentMinor = spent,
        )
    }
}

/**
 * Whether [record] counts against this budget.
 *
 * A budget naming a subcategory counts **only** that subcategory; one naming just a
 * category counts every transaction filed under it, including those in its
 * subcategories. That asymmetry is deliberate — a "Food" budget is the whole food
 * allowance, while a "Food · Fast Food" budget is a limit on one slice of it.
 */
private fun Budget.covers(record: Transaction): Boolean {
    if (record.categoryId != categoryId) return false
    return subcategoryId == null || record.subcategoryId == subcategoryId
}
