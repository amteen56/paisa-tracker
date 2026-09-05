package com.amteen.paisa.ui.navigation

import java.time.LocalDate

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
    /**
     * Add screen. `type` is EXPENSE or INCOME; the form is shared.
     *
     * `date` is optional and only used by the calendar's day sheet, which has to open
     * the form on the day the user was looking at rather than on today. ISO-8601, so
     * it survives process death as a plain string.
     */
    const val ADD_TRANSACTION_ROUTE = "transaction/add/{type}?date={date}"
    const val ARG_TYPE = "type"
    const val ARG_DATE = "date"

    fun addTransaction(type: String, date: LocalDate? = null): String =
        if (date == null) "transaction/add/$type" else "transaction/add/$type?date=$date"

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

    /**
     * The bare path, with no day preselected. Kept as its own constant because the
     * More menu navigates by plain string, and because it is what [calendar] returns
     * when there is no day to open.
     */
    const val CALENDAR = "calendar"

    /**
     * Calendar screen. `date` is optional and only used by Home's seven-day strip,
     * which opens the calendar on the day the user tapped rather than on today.
     * ISO-8601, so it survives process death as a plain string.
     */
    const val CALENDAR_ROUTE = "calendar?date={date}"

    fun calendar(date: LocalDate? = null): String =
        if (date == null) CALENDAR else "calendar?date=$date"
    // No CURRENCIES / EXCHANGE_RATES route: the app is PKR-only. See CLAUDE.md.
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
