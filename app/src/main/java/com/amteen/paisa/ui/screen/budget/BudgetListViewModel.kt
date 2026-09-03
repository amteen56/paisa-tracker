package com.amteen.paisa.ui.screen.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import com.amteen.paisa.domain.usecase.ArchiveBudgetUseCase
import com.amteen.paisa.domain.usecase.BudgetSummary
import com.amteen.paisa.domain.usecase.DeleteBudgetUseCase
import com.amteen.paisa.domain.usecase.GetBudgetStatusUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * The budgets list.
 *
 * The month is the only real input: everything shown is derived from it and the
 * ledger by [GetBudgetStatusUseCase], the same derivation the dashboard strip uses,
 * so the two screens can never disagree about how much of a budget is gone.
 */
class BudgetListViewModel(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    private val budgetStatus: GetBudgetStatusUseCase,
    private val archiveBudget: ArchiveBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val chrome = MutableStateFlow(Chrome())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BudgetListUiState> = combine(
        month,
        budgetRepository.budgets,
        categoryRepository.categories,
        currencyRepository.currencies,
        settingsRepository.settings,
    ) { currentMonth, budgets, categories, currencies, settings ->
        Inputs(currentMonth, budgets, categories, currencies, settings.baseCurrencyCode, settings.budgetAlertsEnabled)
    }.flatMapLatest { inputs ->
        // Only the selected month's shard is read, so paging back through history
        // costs one file per step rather than the whole ledger.
        transactionRepository.observeRange(DateRange.of(inputs.month))
            .combine(chrome) { records, flags -> build(inputs, records, flags) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetListUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            budgetRepository.load()
        }
    }

    private fun build(
        inputs: Inputs,
        records: List<Transaction>,
        flags: Chrome,
    ): BudgetListUiState {
        val table = CurrencyTable(inputs.currencies, inputs.baseCurrencyCode)

        val live = inputs.budgets.filterNot { it.archived }
        val summaries = budgetStatus(live, records, inputs.month, table, inputs.categories)

        // Archived budgets still get real figures — an archived limit with spending
        // against it is exactly what someone reviewing an old month wants to see.
        val archived = inputs.budgets.filter { it.archived }.map { budget ->
            val category = inputs.categories.firstOrNull { it.id == budget.categoryId }
            BudgetSummary(
                progress = budgetStatus.progressFor(budget, records, inputs.month, table),
                category = category,
                subcategory = category?.subcategory(budget.subcategoryId),
                currency = table.currency(budget.currencyCode),
            )
        }

        val totals = totals(summaries, table)

        return BudgetListUiState(
            isLoading = false,
            month = inputs.month,
            summaries = summaries,
            archived = archived,
            archivedVisible = flags.archivedVisible,
            totalLimit = totals?.limit,
            totalSpent = totals?.spent,
            totalsMixedCurrency = totals?.mixed ?: false,
            baseCurrency = table.base,
            alertsEnabled = inputs.alertsEnabled,
            // Only worth asking once there is something to be alerted about. Asking
            // on first launch, before the user has any budgets, is a permission
            // prompt with no visible reason attached to it.
            shouldRequestNotificationPermission = summaries.isNotEmpty() && inputs.alertsEnabled,
            pendingRemoval = flags.pendingRemoval,
            message = flags.message,
            error = flags.error,
        )
    }

    /**
     * Adds up the limits and the spending across every budget.
     *
     * Budgets may each be in a different currency, so both figures are converted into
     * the base. That conversion is the whole reason [Totals.mixed] exists — the UI
     * has to say the number rests on manual rates.
     */
    private fun totals(summaries: List<BudgetSummary>, table: CurrencyTable): Totals? {
        if (summaries.isEmpty()) return null
        var limit = 0L
        var spent = 0L
        var mixed = false
        for (summary in summaries) {
            if (summary.progress.budget.currencyCode != table.base.code) mixed = true
            limit += table.toBase(summary.progress.budget.limit).amountMinor
            spent += table.toBase(summary.progress.spent).amountMinor
        }
        return Totals(
            limit = Money(limit, table.base.code),
            spent = Money(spent, table.base.code),
            mixed = mixed,
        )
    }

    fun onEvent(event: BudgetListEvent) {
        when (event) {
            BudgetListEvent.PreviousMonth -> month.update { it.minusMonths(1) }
            BudgetListEvent.NextMonth -> month.update { it.plusMonths(1) }
            BudgetListEvent.ThisMonth -> month.value = YearMonth.now()

            BudgetListEvent.ToggleArchivedVisible ->
                chrome.update { it.copy(archivedVisible = !it.archivedVisible) }

            is BudgetListEvent.RemoveRequested -> {
                val summary = (uiState.value.summaries + uiState.value.archived)
                    .firstOrNull { it.id == event.id } ?: return
                chrome.update {
                    it.copy(pendingRemoval = PendingBudgetRemoval(summary.id, summary.label))
                }
            }

            BudgetListEvent.RemoveDismissed -> chrome.update { it.copy(pendingRemoval = null) }

            BudgetListEvent.RemoveConfirmed -> {
                val pending = chrome.value.pendingRemoval ?: return
                chrome.update { it.copy(pendingRemoval = null) }
                viewModelScope.launch {
                    when (val result = deleteBudget(pending.id)) {
                        is AppResult.Ok ->
                            chrome.update { it.copy(message = "${pending.label} budget deleted.") }
                        is AppResult.Err ->
                            chrome.update { it.copy(error = result.error.displayMessage) }
                    }
                }
            }

            is BudgetListEvent.ArchiveToggled -> viewModelScope.launch {
                when (val result = archiveBudget(event.id, event.archived)) {
                    is AppResult.Ok -> chrome.update {
                        it.copy(
                            message = if (event.archived) "Budget archived." else "Budget restored.",
                        )
                    }
                    is AppResult.Err ->
                        chrome.update { it.copy(error = result.error.displayMessage) }
                }
            }

            is BudgetListEvent.AlertsToggled -> viewModelScope.launch {
                settingsRepository.update { it.copy(budgetAlertsEnabled = event.enabled) }
            }

            // The screen has asked the system; nothing to remember here, because the
            // answer lives with the OS rather than in our settings file.
            BudgetListEvent.NotificationPermissionRequested -> Unit

            BudgetListEvent.MessageShown -> chrome.update { it.copy(message = null) }
            BudgetListEvent.DismissError -> chrome.update { it.copy(error = null) }
        }
    }

    private data class Inputs(
        val month: YearMonth,
        val budgets: List<Budget>,
        val categories: List<Category>,
        val currencies: List<Currency>,
        val baseCurrencyCode: String,
        val alertsEnabled: Boolean,
    )

    private data class Chrome(
        val archivedVisible: Boolean = false,
        val pendingRemoval: PendingBudgetRemoval? = null,
        val message: String? = null,
        val error: String? = null,
    )

    private data class Totals(val limit: Money, val spent: Money, val mixed: Boolean)
}
