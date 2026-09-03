package com.amteen.paisa.domain.model

/** Which transaction types a category may be chosen for. */
enum class CategoryScope {
    EXPENSE,
    INCOME,
    BOTH;

    fun allows(type: TransactionType): Boolean = when (this) {
        EXPENSE -> type.isExpense
        INCOME -> type.isIncome
        BOTH -> true
    }

    /**
     * Whether a category of this scope belongs on a list showing [tab].
     *
     * A `BOTH` category appears under Expense *and* Income, which is the point of
     * it — "Gifts" is money that moves in either direction for many people.
     */
    fun overlaps(tab: CategoryScope): Boolean =
        this == BOTH || tab == BOTH || this == tab
}

data class Subcategory(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/**
 * A spending or income category.
 *
 * Subcategories are nested rather than kept in their own file: they are only ever
 * read, edited and rendered alongside their parent, so a separate file would add
 * a join and a second chance to end up inconsistent.
 *
 * [archived] rather than deletion — a category referenced by even one transaction
 * must keep resolving forever, or history and reports start rendering blanks.
 * Hard delete is permitted only at reference count zero. See CLAUDE.md rule 4.
 */
data class Category(
    val id: String,
    val name: String,
    val applicableTo: CategoryScope,
    val iconKey: String,
    val colorArgb: Int,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val subcategories: List<Subcategory> = emptyList(),
) {
    /** Subcategories offered in a picker — archived ones stay out of new entries. */
    val activeSubcategories: List<Subcategory>
        get() = subcategories.filterNot { it.archived }.sortedBy { it.sortOrder }

    fun subcategory(id: String?): Subcategory? =
        if (id == null) null else subcategories.firstOrNull { it.id == id }
}
