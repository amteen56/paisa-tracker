package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.domain.model.BudgetProgress
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import java.time.LocalDate
import java.time.YearMonth

/**
 * How one budget has fared over recent months, newest first.
 *
 * A one-shot `suspend` read rather than a flow: this reaches back over several
 * shards, and holding all of them open for a screen the user glances at would
 * undo the point of loading months lazily.
 */
class GetBudgetHistoryUseCase(
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val budgetStatus: GetBudgetStatusUseCase = GetBudgetStatusUseCase(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend operator fun invoke(
        budgetId: String,
        months: Int = DEFAULT_MONTHS,
    ): List<BudgetProgress> {
        val budget = budgets.getById(budgetId) ?: return emptyList()

        settings.load()
        currencies.load()
        val table = CurrencyTable(
            currencies.currencies.value,
            settings.settings.value.baseCurrencyCode,
        )

        val current = YearMonth.from(today())
        // A budget pinned to one month has exactly one month of history, and showing
        // it a row of zeroes for months it was never in force would read as months
        // where the user spent nothing.
        val window = budget.period?.let { listOf(it) }
            ?: (0 until months.coerceAtLeast(1)).map { current.minusMonths(it.toLong()) }

        if (window.isEmpty()) return emptyList()

        val range = DateRange(window.min().atDay(1), window.max().atEndOfMonth())
        val records = transactions.getRange(range)

        // progressFor rather than invoke: history must still compute for an archived
        // budget, which is precisely the kind a user looks back at.
        return window.map { month -> budgetStatus.progressFor(budget, records, month, table) }
    }

    companion object {
        const val DEFAULT_MONTHS = 6
    }
}
