package com.amteen.paisa.data.seed

import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Subcategory

/**
 * What a brand-new install starts with.
 *
 * IDs are **stable slugs, not UUIDs**, on purpose: a backup taken on one device
 * and restored on another must land on the same categories rather than duplicating
 * every default. User-created records get UUIDs.
 *
 * The set is opinionated and Pakistan-first (PKR base, mobile wallets, no sales-tax
 * category) because a good default list the user prunes beats an empty screen.
 */
object DefaultData {

    const val BASE_CURRENCY_CODE = "PKR"
    const val UNCATEGORIZED_ID = "cat-other-expense"

    /**
     * **Paisa is PKR-only.** Multi-currency was cut from the product, so this list has
     * exactly one entry and no screen can add to it — see CLAUDE.md, *Single currency*.
     *
     * It stays a list rather than a bare constant because `Currency` is what carries the
     * symbol and `decimalDigits` that `MoneyFormatter` formats with, and because keeping
     * the shape means the on-disk JSON never needed a breaking change. Do not read that
     * as an invitation to seed a second entry.
     */
    val currency: Currency = Currency(BASE_CURRENCY_CODE, "Pakistani Rupee", "Rs.", 2, 1.0)

    val currencies: List<Currency> = listOf(currency)

    val paymentMethods: List<PaymentMethod> = listOf(
        PaymentMethod("pm-cash", "Cash", "cash", 0),
        PaymentMethod("pm-debit", "Debit Card", "card", 1),
        PaymentMethod("pm-credit", "Credit Card", "credit-card", 2),
        PaymentMethod("pm-bank", "Bank Transfer", "bank", 3),
        PaymentMethod("pm-wallet", "Mobile Wallet", "wallet", 4),
        PaymentMethod("pm-cheque", "Cheque", "receipt", 5),
    )

    val categories: List<Category> = buildList {
        var order = 0

        // -- Expenses ---------------------------------------------------------
        add(
            expense(
                id = "cat-food", name = "Food & Drink", icon = "restaurant",
                color = 0xFFEF6C00.toInt(), order = order++,
                subs = listOf("Groceries", "Restaurants", "Fast Food", "Coffee & Tea", "Deliveries"),
            ),
        )
        add(
            expense(
                id = "cat-transport", name = "Transport", icon = "car",
                color = 0xFF1565C0.toInt(), order = order++,
                subs = listOf("Fuel", "Ride-hailing", "Public Transport", "Parking", "Maintenance"),
            ),
        )
        add(
            expense(
                id = "cat-housing", name = "Housing", icon = "home",
                color = 0xFF6D4C41.toInt(), order = order++,
                subs = listOf("Rent", "Repairs", "Furniture", "Society Charges"),
            ),
        )
        add(
            expense(
                id = "cat-bills", name = "Bills & Utilities", icon = "receipt",
                color = 0xFF00838F.toInt(), order = order++,
                subs = listOf("Electricity", "Gas", "Water", "Internet", "Mobile", "Subscriptions"),
            ),
        )
        add(
            expense(
                id = "cat-shopping", name = "Shopping", icon = "shopping-bag",
                color = 0xFFAD1457.toInt(), order = order++,
                subs = listOf("Clothing", "Electronics", "Household", "Personal Care"),
            ),
        )
        add(
            expense(
                id = "cat-health", name = "Health", icon = "health",
                color = 0xFFC62828.toInt(), order = order++,
                subs = listOf("Doctor", "Pharmacy", "Tests & Labs", "Fitness", "Insurance"),
            ),
        )
        add(
            expense(
                id = "cat-education", name = "Education", icon = "school",
                color = 0xFF4527A0.toInt(), order = order++,
                subs = listOf("Tuition & Fees", "Books", "Courses", "Stationery"),
            ),
        )
        add(
            expense(
                id = "cat-entertainment", name = "Entertainment", icon = "movie",
                color = 0xFF7B1FA2.toInt(), order = order++,
                subs = listOf("Streaming", "Cinema", "Games", "Outings", "Hobbies"),
            ),
        )
        add(
            expense(
                id = "cat-family", name = "Family & Kids", icon = "family",
                color = 0xFFD81B60.toInt(), order = order++,
                subs = listOf("Childcare", "School", "Toys", "Allowance"),
            ),
        )
        add(
            expense(
                id = "cat-travel", name = "Travel", icon = "flight",
                color = 0xFF0277BD.toInt(), order = order++,
                subs = listOf("Flights", "Hotels", "Local Transport", "Food & Sightseeing"),
            ),
        )
        add(
            expense(
                id = "cat-gifts", name = "Gifts & Donations", icon = "gift",
                color = 0xFF2E7D32.toInt(), order = order++,
                subs = listOf("Gifts", "Charity", "Zakat"),
            ),
        )
        add(
            expense(
                id = "cat-fees", name = "Fees & Charges", icon = "bank",
                color = 0xFF455A64.toInt(), order = order++,
                subs = listOf("Bank Charges", "Taxes", "Fines", "Interest"),
            ),
        )
        add(
            expense(
                id = UNCATEGORIZED_ID, name = "Other", icon = "category",
                color = 0xFF546E7A.toInt(), order = order++,
                subs = emptyList(),
            ),
        )

        // -- Income -----------------------------------------------------------
        add(
            income(
                id = "cat-salary", name = "Salary", icon = "wallet",
                color = 0xFF2E7D32.toInt(), order = order++,
                subs = listOf("Base Pay", "Bonus", "Overtime", "Allowances"),
            ),
        )
        add(
            income(
                id = "cat-business", name = "Business", icon = "store",
                color = 0xFF1B5E20.toInt(), order = order++,
                subs = listOf("Sales", "Services", "Commission"),
            ),
        )
        add(
            income(
                id = "cat-freelance", name = "Freelance", icon = "laptop",
                color = 0xFF00695C.toInt(), order = order++,
                subs = listOf("Projects", "Consulting", "Royalties"),
            ),
        )
        add(
            income(
                id = "cat-investments", name = "Investments", icon = "trending-up",
                color = 0xFF00796B.toInt(), order = order++,
                subs = listOf("Dividends", "Profit & Interest", "Capital Gains"),
            ),
        )
        add(
            income(
                id = "cat-rental", name = "Rental Income", icon = "home",
                color = 0xFF33691E.toInt(), order = order++,
                subs = listOf("Property", "Vehicle", "Equipment"),
            ),
        )
        add(
            income(
                id = "cat-other-income", name = "Other Income", icon = "category",
                color = 0xFF558B2F.toInt(), order = order++,
                subs = listOf("Gifts Received", "Refunds", "Reimbursements"),
            ),
        )
    }

    private fun expense(
        id: String,
        name: String,
        icon: String,
        color: Int,
        order: Int,
        subs: List<String>,
    ) = Category(
        id = id,
        name = name,
        applicableTo = CategoryScope.EXPENSE,
        iconKey = icon,
        colorArgb = color,
        sortOrder = order,
        subcategories = subs.toSubcategories(id),
    )

    private fun income(
        id: String,
        name: String,
        icon: String,
        color: Int,
        order: Int,
        subs: List<String>,
    ) = Category(
        id = id,
        name = name,
        applicableTo = CategoryScope.INCOME,
        iconKey = icon,
        colorArgb = color,
        sortOrder = order,
        subcategories = subs.toSubcategories(id),
    )

    /** Slug ids derived from the parent, so they are stable across installs too. */
    private fun List<String>.toSubcategories(parentId: String): List<Subcategory> =
        mapIndexed { index, label ->
            Subcategory(
                id = "$parentId-${label.slug()}",
                name = label,
                sortOrder = index,
            )
        }

    private fun String.slug(): String =
        lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .split('-')
            .filter { it.isNotEmpty() }
            .joinToString("-")
}
