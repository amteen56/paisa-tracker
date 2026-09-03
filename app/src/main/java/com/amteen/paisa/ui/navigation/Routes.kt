package com.amteen.paisa.ui.navigation

/**
 * Every navigation destination in the app.
 *
 * Routes are plain strings so the NavHost stays framework-version-agnostic.
 * Destinations that take arguments expose a `create(...)` helper — always use it
 * rather than building the path by hand at the call site.
 */
object Routes {

    // --- Bottom navigation -------------------------------------------------
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val REPORTS = "reports"
    const val MORE = "more"

    // --- Transactions ------------------------------------------------------
    /** Add screen. `type` is EXPENSE or INCOME; the form is shared. */
    const val ADD_TRANSACTION_ROUTE = "transaction/add/{type}"
    const val ARG_TYPE = "type"
    fun addTransaction(type: String) = "transaction/add/$type"

    const val EDIT_TRANSACTION_ROUTE = "transaction/edit/{id}"
    fun editTransaction(id: String) = "transaction/edit/$id"

    const val TRANSACTION_DETAIL_ROUTE = "transaction/{id}"
    const val ARG_ID = "id"
    fun transactionDetail(id: String) = "transaction/$id"

    // --- Categories --------------------------------------------------------
    const val CATEGORIES = "categories"

    /**
     * Both arguments are optional. `id` absent means "new"; `scope` carries which
     * tab the user was on, so a category created from the Income tab starts as
     * income rather than making them fix it after the fact.
     */
    const val CATEGORY_EDIT_ROUTE = "categories/edit?id={id}&scope={scope}"
    const val ARG_SCOPE = "scope"

    fun categoryEdit(id: String? = null, scope: String? = null): String {
        val params = buildList {
            if (id != null) add("id=$id")
            if (scope != null) add("scope=$scope")
        }
        return if (params.isEmpty()) {
            "categories/edit"
        } else {
            "categories/edit?" + params.joinToString("&")
        }
    }

    // --- Finance -----------------------------------------------------------
    const val BUDGETS = "budgets"
    const val BUDGET_EDIT_ROUTE = "budgets/edit?id={id}"
    fun budgetEdit(id: String? = null) =
        if (id == null) "budgets/edit" else "budgets/edit?id=$id"

    const val CALENDAR = "calendar"
    const val CURRENCIES = "currencies"
    const val EXCHANGE_RATES = "exchange-rates"
    const val PAYMENT_METHODS = "payment-methods"

    // --- Data --------------------------------------------------------------
    const val BACKUP = "backup"

    // --- Settings ----------------------------------------------------------
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

/** Values for [Routes.ARG_TYPE]. Kept as strings to stay safe across process death. */
object TransactionTypeArg {
    const val EXPENSE = "expense"
    const val INCOME = "income"
}
