package com.amteen.paisa.ui.screen.budget

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.usecase.BudgetSummary
import java.time.YearMonth

/**
 * The budgets screen.
 *
 * [summaries] are live budgets for [month], already sorted closest-to-limit first by
 * `GetBudgetStatusUseCase`. [archived] are kept separate rather than filtered out, so
 * a retired budget can still be found and restored.
 */
data class BudgetListUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summaries: List<BudgetSummary> = emptyList(),
    val archived: List<BudgetSummary> = emptyList(),
    val archivedVisible: Boolean = false,

    /**
     * Totals across every budget, converted into the base currency.
     *
     * `null` when there is nothing to total. [totalsMixedCurrency] says whether any
     * budget had to be converted to get here, because a figure resting on the user's
     * manual rates has to say so — CLAUDE.md rule 5.
     */
    val totalLimit: Money? = null,
    val totalSpent: Money? = null,
    val totalsMixedCurrency: Boolean = false,
    val baseCurrency: Currency = CurrencyTable.fallback("PKR"),

    val alertsEnabled: Boolean = true,
    /** Set once the user has budgets, to ask for notification permission in context. */
    val shouldRequestNotificationPermission: Boolean = false,

    val pendingRemoval: PendingBudgetRemoval? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && summaries.isEmpty() && archived.isEmpty()

    /** True when looking at a month other than the one we are in. */
    val isHistoric: Boolean get() = month < YearMonth.now()
}

/** A budget the user has asked to remove, held while the dialog is open. */
data class PendingBudgetRemoval(
    val id: String,
    val label: String,
)

sealed interface BudgetListEvent {
    data object PreviousMonth : BudgetListEvent
    data object NextMonth : BudgetListEvent
    data object ThisMonth : BudgetListEvent

    data object ToggleArchivedVisible : BudgetListEvent
    data class RemoveRequested(val id: String) : BudgetListEvent
    data object RemoveConfirmed : BudgetListEvent
    data object RemoveDismissed : BudgetListEvent
    data class ArchiveToggled(val id: String, val archived: Boolean) : BudgetListEvent

    data class AlertsToggled(val enabled: Boolean) : BudgetListEvent
    data object NotificationPermissionRequested : BudgetListEvent

    data object MessageShown : BudgetListEvent
    data object DismissError : BudgetListEvent
}
