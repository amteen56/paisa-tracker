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
     * Totals across every budget. `null` when there is nothing to total.
     *
     * A plain sum: every limit is PKR, so there is nothing to convert and nothing to
     * disclose as converted.
     */
    val totalLimit: Money? = null,
    val totalSpent: Money? = null,
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

    /**
     * A drag moved [fromId] to where [toId] was. Optimistic only — the new order is
     * held in the ViewModel and written once on [ReorderCommitted].
     */
    data class Moved(val fromId: String, val toId: String) : BudgetListEvent

    /** The finger lifted: persist whatever the drag arrived at. */
    data object ReorderCommitted : BudgetListEvent

    /**
     * One step up or down. A long-press drag is not operable with TalkBack on, so it
     * can never be the only way to reorder — see `DragDropList`.
     */
    data class MoveStep(val id: String, val up: Boolean) : BudgetListEvent

    data class AlertsToggled(val enabled: Boolean) : BudgetListEvent
    data object NotificationPermissionRequested : BudgetListEvent

    data object MessageShown : BudgetListEvent
    data object DismissError : BudgetListEvent
}
