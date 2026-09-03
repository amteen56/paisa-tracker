package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.domain.model.BudgetAlert
import com.amteen.paisa.domain.model.BudgetAlertThresholds
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.repository.BudgetAlertStateRepository
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import java.time.LocalDate
import java.time.YearMonth

/**
 * One budget that has just crossed a threshold, and everything needed to say so.
 *
 * [newlyCrossed] is the highest threshold crossed since the last check — that is the
 * one the user is told about. [toRecord] is every threshold now at or below the
 * budget's usage, all of which are marked as announced so none of them can fire
 * again later.
 */
data class BudgetAlertEvent(
    val summary: BudgetSummary,
    val newlyCrossed: Int,
    val toRecord: List<BudgetAlert>,
)

/**
 * Decides which budget alerts should fire, without knowing what a notification is.
 *
 * The Android half lives in `notification/BudgetAlertNotifier`. The split is what
 * makes "once per threshold per period" — the only genuinely fiddly part — testable
 * on the JVM, where an off-by-one in the threshold comparison is cheap to catch.
 *
 * Deliberately **not** a flow. Alerting is an event, not a state: subscribing to the
 * ledger and firing on every emission would notify the user while they are still
 * typing the transaction that crossed the line.
 */
class EvaluateBudgetAlertsUseCase(
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val alertState: BudgetAlertStateRepository,
    private val budgetStatus: GetBudgetStatusUseCase = GetBudgetStatusUseCase(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend operator fun invoke(): List<BudgetAlertEvent> {
        settings.load()
        if (!settings.settings.value.budgetAlertsEnabled) return emptyList()

        budgets.load()
        val live = budgets.budgets.value
        if (live.isEmpty()) return emptyList()

        categories.load()
        currencies.load()
        alertState.load()

        val month = YearMonth.from(today())
        val records = transactions.getRange(DateRange.of(month))
        val table = CurrencyTable(currencies.currencies.value, settings.settings.value.baseCurrencyCode)

        val already = alertState.fired.value
        val events = budgetStatus(
            budgets = live,
            transactions = records,
            month = month,
            table = table,
            categories = categories.categories.value,
        ).mapNotNull { summary ->
            // A limit of zero would put every budget at 0% by BudgetProgress's own
            // definition, so it can never cross anything. Guarded anyway, because a
            // hand-edited file can carry one.
            if (summary.progress.budget.limitMinor <= 0L) return@mapNotNull null

            val percent = summary.progress.percent
            val crossed = BudgetAlertThresholds.all.filter { percent >= it }
            if (crossed.isEmpty()) return@mapNotNull null

            val alertRecords = crossed.map { threshold ->
                BudgetAlert(summary.id, month, threshold)
            }
            if (alertRecords.all { it in already }) return@mapNotNull null

            BudgetAlertEvent(
                summary = summary,
                // Only the highest is announced. Someone who adds one large expense
                // can cross 75, 90 and 100 at once, and three notifications about
                // one purchase is three chances for the user to turn alerts off.
                // `crossed` comes from an ascending list, so the last is the highest.
                newlyCrossed = crossed.last(),
                toRecord = alertRecords,
            )
        }

        return events
    }

    /**
     * Marks [events] as announced, so they cannot fire again this period, and drops
     * records old enough that nothing will ever consult them again.
     *
     * Separate from [invoke] so a caller that fails to actually show the
     * notification — permission denied, notifications disabled at the OS level —
     * does not silently burn the alert.
     */
    suspend fun markShown(events: List<BudgetAlertEvent>) {
        if (events.isEmpty()) return
        alertState.record(events.flatMap { it.toRecord })
        alertState.pruneBefore(YearMonth.from(today()).minusMonths(RETENTION_MONTHS))
    }

    private companion object {
        /**
         * How far back alert records are kept. Only the current month is ever
         * consulted; the margin is for a device whose clock moves backwards, and for
         * anyone curious enough to read the file.
         */
        const val RETENTION_MONTHS = 3L
    }
}
