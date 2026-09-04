package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.model.TransactionTotals
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** One category's share of this month's spending, already converted to base. */
data class CategorySpend(
    val categoryId: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val amountMinor: Long,
    val currencyCode: String,
    /** 0f..1f of the month's total expense. Display only — never money. */
    val share: Float,
) {
    val amount: Money get() = Money(amountMinor, currencyCode)
}

/** One day's expense total for the seven-day chart. Zero-filled, so gaps are real. */
data class DailySpend(
    val date: LocalDate,
    val amountMinor: Long,
    val currencyCode: String,
) {
    val amount: Money get() = Money(amountMinor, currencyCode)
}

/**
 * Everything the dashboard renders, derived in one pass over one range of shards.
 *
 * Assembled here rather than in the ViewModel so the figures are testable without
 * Android, and so a composable never has to sum anything to draw itself.
 */
data class DashboardSummary(
    val today: LocalDate,
    val month: YearMonth,
    val baseCurrency: Currency,

    /** Income, expense and net for the current month, converted to base. */
    val totals: TransactionTotals,

    val todaySpentMinor: Long,

    /**
     * Average spending per day **this month so far** — this month's expense divided
     * by the days elapsed.
     *
     * This replaced a rolling ten-day window. The original objection to month-to-date
     * was that a true monthly average means loading every shard, which is the cost
     * the lazy-loading design exists to avoid — but that applies to a *multi-month*
     * average. Month-to-date needs only the current month's shard, which is already
     * loaded, so the cost argument never applied to it.
     *
     * The trade-off that is real: on the 1st this is a single day of noise, and by
     * the 30th it is smoothed enough that a change in habit takes a while to surface.
     * That is accepted, because it is the figure people recognise and can check
     * against the month's total themselves.
     */
    val dailyAverageMinor: Long,

    /**
     * The divisor actually used — days elapsed this month, unless the user has been
     * tracking for fewer days than that. Dividing someone who installed on the 25th
     * by 25 would report an average over 20 days they were not tracking.
     */
    val averageDays: Int,

    /**
     * This month's expense up to and including today.
     *
     * Distinct from `totals.expense`, which covers the whole month: a transaction the
     * user dated later this month belongs in the month's total but not in a
     * to-date figure.
     */
    val monthToDateExpenseMinor: Long,

    /**
     * The same stretch of last month, for a like-for-like comparison. Comparing
     * eleven days against a full previous month would always flatter the user.
     */
    val previousMonthToDateExpenseMinor: Long,

    val topCategories: List<CategorySpend>,
    val budgets: List<BudgetSummary>,
    val recent: List<TransactionDetails>,
    /** Exactly seven entries, oldest first, ending on [today]. */
    val dailySpend: List<DailySpend>,

    val hasAnyTransactions: Boolean,
) {
    val todaySpent: Money get() = Money(todaySpentMinor, baseCurrency.code)
    val dailyAverage: Money get() = Money(dailyAverageMinor, baseCurrency.code)

    /**
     * Change against the same point last month, as a percentage of last month.
     *
     * Both sides are **to-date**: this month up to today against the same elapsed
     * days of last month. Using the whole month on this side — which is what it did
     * before — makes the comparison unfair the moment the user dates anything later
     * in the month, because a future entry counts on one side and has no counterpart
     * on the other.
     *
     * `null` when there is nothing to compare against — a jump from zero is not
     * "up 100%", it is simply the first month with any spending in it, and dressing
     * that up as a percentage says nothing.
     */
    val expenseChangePercent: Double?
        get() {
            if (previousMonthToDateExpenseMinor <= 0L) return null
            val difference = monthToDateExpenseMinor - previousMonthToDateExpenseMinor
            return difference.toDouble() / previousMonthToDateExpenseMinor.toDouble() * 100.0
        }

    companion object {
        /** Days in the bar chart. A week, so the weekday labels mean something. */
        const val DAY_WINDOW = 7

        const val TOP_CATEGORY_COUNT = 5
        const val RECENT_COUNT = 5
    }
}

/**
 * The dashboard, derived from the ledger.
 *
 * Loads **two** month shards — the current one and the one before it. The previous
 * month earns its place twice over: the month-on-month comparison needs both sides,
 * and the seven-day window straddles the boundary for the first week of every month.
 * Older history stays on disk until a report actually asks for it.
 */
class GetDashboardSummaryUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val budgets: BudgetRepository,
    private val budgetStatus: GetBudgetStatusUseCase = GetBudgetStatusUseCase(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<DashboardSummary> {
        val references = combine(
            settings.settings,
            currencies.currencies,
            categories.categories,
            paymentMethods.paymentMethods,
            budgets.budgets,
        ) { appSettings, currencyList, categoryList, methodList, budgetList ->
            References(appSettings, currencyList, categoryList, methodList, budgetList)
        }

        return references.flatMapLatest { refs ->
            // The clock is read inside the flow, so a dashboard left open overnight
            // recomputes against the real date the next time anything changes.
            val now = today()
            val month = YearMonth.from(now)
            val range = DateRange(month.minusMonths(1).atDay(1), month.atEndOfMonth())
            transactions.observeRange(range).map { records -> build(records, refs, now, month) }
        }
    }

    private fun build(
        records: List<Transaction>,
        refs: References,
        now: LocalDate,
        month: YearMonth,
    ): DashboardSummary {
        val table = CurrencyTable(refs.currencies, refs.settings.baseCurrencyCode)
        val base = table.base
        val previousMonth = month.minusMonths(1)
        val windowStart = now.minusDays((DashboardSummary.DAY_WINDOW - 1).toLong())

        var income = 0L
        var expense = 0L
        var todaySpent = 0L
        var previousToDate = 0L

        /**
         * This month's expense up to and including today, which is *not* the same as
         * [expense]: a transaction the user dated later this month belongs in the
         * month's total but must not be divided by the days elapsed so far.
         */
        var monthToDateExpense = 0L
        var earliest: LocalDate? = null

        val byCategory = HashMap<String, Long>()
        val byDay = HashMap<LocalDate, Long>()

        for (record in records) {
            // Tracked across every record, income included: how long the user has
            // been using the app is what bounds the average's divisor, and that has
            // nothing to do with which of their entries were expenses.
            if (earliest == null || record.date < earliest) earliest = record.date

            val recordMonth = YearMonth.from(record.date)
            val isThisMonth = recordMonth == month
            val inBase = table.toBase(record.money).amountMinor


            if (record.type.isIncome) {
                if (isThisMonth) income += inBase
                continue
            }

            // The seven-day chart is built from the whole range rather than this
            // month alone: early in any month most of that window is last month.
            if (record.date >= windowStart && record.date <= now) {
                byDay[record.date] = (byDay[record.date] ?: 0L) + inBase
            }

            when {
                isThisMonth -> {
                    expense += inBase
                    if (record.date <= now) monthToDateExpense += inBase
                    if (record.date == now) todaySpent += inBase
                    byCategory[record.categoryId] = (byCategory[record.categoryId] ?: 0L) + inBase
                }
                // Same elapsed days of last month. On the 31st against a 28-day
                // February this takes the whole month, which is the closest
                // like-for-like available and never overstates the comparison.
                recordMonth == previousMonth && record.date.dayOfMonth <= now.dayOfMonth ->
                    previousToDate += inBase
            }
        }

        val averageDays = averageDivisor(earliest, month.atDay(1), now)

        return DashboardSummary(
            today = now,
            month = month,
            baseCurrency = base,
            totals = TransactionTotals(
                income = Money(income, base.code),
                expense = Money(expense, base.code),
                count = records.count { YearMonth.from(it.date) == month },
            ),
            todaySpentMinor = todaySpent,
            dailyAverageMinor = divideRounded(monthToDateExpense, averageDays),
            averageDays = averageDays,
            monthToDateExpenseMinor = monthToDateExpense,
            previousMonthToDateExpenseMinor = previousToDate,
            topCategories = topCategories(byCategory, expense, refs.categories, base.code),
            budgets = budgetStatus(
                budgets = refs.budgets,
                transactions = records,
                month = month,
                table = table,
                categories = refs.categories,
            ),
            recent = recent(records, refs, table),
            dailySpend = dailySpend(byDay, windowStart, base.code),
            hasAnyTransactions = records.isNotEmpty(),
        )
    }

    private fun topCategories(
        byCategory: Map<String, Long>,
        totalExpense: Long,
        categories: List<Category>,
        currencyCode: String,
    ): List<CategorySpend> {
        if (byCategory.isEmpty()) return emptyList()
        val categoryById = categories.associateBy { it.id }

        return byCategory.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(DashboardSummary.TOP_CATEGORY_COUNT)
            .map { (id, amount) ->
                val category = categoryById[id]
                CategorySpend(
                    categoryId = id,
                    name = category?.name ?: "Uncategorised",
                    iconKey = category?.iconKey.orEmpty(),
                    // A missing category still needs a colour, or its bar renders
                    // invisible against the track.
                    colorArgb = category?.colorArgb ?: FALLBACK_CATEGORY_COLOR,
                    amountMinor = amount,
                    currencyCode = currencyCode,
                    share = if (totalExpense <= 0L) 0f
                    else (amount.toDouble() / totalExpense.toDouble()).toFloat(),
                )
            }
    }

    private fun recent(
        records: List<Transaction>,
        refs: References,
        table: CurrencyTable,
    ): List<TransactionDetails> {
        val categoryById = refs.categories.associateBy { it.id }
        val methodById = refs.paymentMethods.associateBy { it.id }

        return records
            .sortedWith(
                compareByDescending<Transaction> { it.date }
                    .thenByDescending { it.time }
                    .thenBy { it.id },
            )
            .take(DashboardSummary.RECENT_COUNT)
            .map { record ->
                val category = categoryById[record.categoryId]
                TransactionDetails(
                    transaction = record,
                    category = category,
                    subcategory = category?.subcategory(record.subcategoryId),
                    paymentMethod = methodById[record.paymentMethodId],
                    currency = table.currency(record.currencyCode),
                )
            }
    }

    /** Zero-filled so an empty day is drawn as an empty day, not skipped over. */
    private fun dailySpend(
        byDay: Map<LocalDate, Long>,
        windowStart: LocalDate,
        currencyCode: String,
    ): List<DailySpend> = (0 until DashboardSummary.DAY_WINDOW).map { offset ->
        val date = windowStart.plusDays(offset.toLong())
        DailySpend(
            date = date,
            amountMinor = byDay[date] ?: 0L,
            currencyCode = currencyCode,
        )
    }

    /**
     * How many days the average should be divided by.
     *
     * Normally the days elapsed this month. For someone who first recorded something
     * on the 25th it is the days since then — dividing by 25 would report an average
     * over 20 days they were not tracking, which is a figure they have never spent.
     *
     * A record dated in the future cannot shorten the span, so a mistyped year cannot
     * inflate the average.
     */
    private fun averageDivisor(
        earliest: LocalDate?,
        monthStart: LocalDate,
        now: LocalDate,
    ): Int {
        val elapsed = now.dayOfMonth
        val first = earliest ?: return elapsed
        val start = maxOf(first, monthStart)
        val days = now.toEpochDay() - start.toEpochDay() + 1
        // Never below one, never above the days actually elapsed this month.
        return days.coerceIn(1L, elapsed.toLong()).toInt()
    }

    /**
     * Integer division, rounded half-up, entirely in `Long`.
     *
     * Going through `Double` here would be the one place a fraction of a rupee could
     * creep into a displayed figure — see CLAUDE.md.
     */
    private fun divideRounded(total: Long, divisor: Int): Long {
        if (divisor <= 0) return 0L
        return (total + divisor / 2) / divisor
    }

    private data class References(
        val settings: AppSettings,
        val currencies: List<Currency>,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
        val budgets: List<Budget>,
    )

    private companion object {
        /** Neutral grey, legible on both themes. */
        const val FALLBACK_CATEGORY_COLOR: Int = 0xFF8E8E93.toInt()
    }
}
