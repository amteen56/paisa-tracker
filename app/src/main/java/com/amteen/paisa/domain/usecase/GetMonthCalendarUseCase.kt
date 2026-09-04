package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.model.TransactionTotals
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * One cell of the month grid.
 *
 * Income and expense are kept **apart** rather than pre-netted. A day that took in
 * Rs. 5,000 and spent Rs. 5,000 is not the same day as one where nothing happened,
 * and a single net figure cannot tell them apart.
 *
 * Both figures are plain sums: every amount in the app is PKR, so there is no
 * conversion step and nothing for the grid to disclose. See CLAUDE.md.
 *
 * [items] carries the day's transactions with their references resolved, so opening
 * the day sheet needs no second read: the day is already inside the range the grid
 * loaded.
 */
data class CalendarDay(
    val date: LocalDate,
    /** False for the leading/trailing days borrowed from a neighbouring month. */
    val inMonth: Boolean,
    val isToday: Boolean,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val currencyCode: String,
    val items: List<TransactionDetails>,
) {
    val income: Money get() = Money(incomeMinor, currencyCode)
    val expense: Money get() = Money(expenseMinor, currencyCode)

    val netMinor: Long get() = incomeMinor - expenseMinor
    val net: Money get() = Money(netMinor, currencyCode)

    val count: Int get() = items.size
    val hasActivity: Boolean get() = items.isNotEmpty()
    val hasIncome: Boolean get() = incomeMinor != 0L
    val hasExpense: Boolean get() = expenseMinor != 0L
}

/**
 * A month laid out as a calendar grid, with each day's figures already derived.
 *
 * [weeks] is always whole rows of seven, starting on the user's
 * [AppSettings.firstDayOfWeek], which is why the first and last rows carry days from
 * the neighbouring months. Those days show their real figures — spending on the 31st
 * and the 1st is one continuous stretch to the person who lived it — but they are
 * excluded from [totals], [peakExpenseMinor] and [activeDayCount], all of which are
 * strictly about *this* month.
 *
 * Every figure is PKR — the app has one currency — so nothing here converts and
 * nothing needs disclosing as converted.
 */
data class MonthCalendar(
    val month: YearMonth,
    val today: LocalDate,
    /** Seven entries, in column order, beginning with the user's first day of week. */
    val weekdays: List<DayOfWeek>,
    /** Five or six rows of exactly seven days. */
    val weeks: List<List<CalendarDay>>,
    val baseCurrency: Currency,
    /** Income, expense and net for this month alone. */
    val totals: TransactionTotals,
    /**
     * The busiest in-month day's expense. The grid scales its per-day bars against
     * this, so the shape of a month reads the same whether the user spends hundreds
     * or hundreds of thousands.
     */
    val peakExpenseMinor: Long,
    /** The in-month day with the most spending — `null` when nothing was spent. */
    val busiestDay: CalendarDay?,
    /** In-month days carrying at least one transaction. */
    val activeDayCount: Int,
) {
    val days: List<CalendarDay> get() = weeks.flatten()

    val daysInMonth: Int get() = month.lengthOfMonth()

    val hasAnyTransactions: Boolean get() = days.any { it.inMonth && it.hasActivity }

    fun day(date: LocalDate): CalendarDay? = days.firstOrNull { it.date == date }
}

/**
 * The month grid, derived from the ledger.
 *
 * `ObserveTransactionsUseCase` already groups by day, but into a flat list of
 * sections carrying one net figure each. A calendar needs something different: whole
 * rows of seven regardless of where the month starts, days from the neighbouring
 * months to fill them, income and expense held separately per cell, and a peak to
 * scale against. Deriving that here rather than in the composable keeps the money
 * work testable and keeps the grid rendering only — see CLAUDE.md.
 *
 * Reads the shards the **grid** spans, not the month: a 42-cell grid can reach into
 * the month either side, so this is at most three files.
 */
class GetMonthCalendarUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(month: Flow<YearMonth>): Flow<MonthCalendar> {
        val references = combine(
            settings.settings,
            currencies.currencies,
            categories.categories,
            paymentMethods.paymentMethods,
        ) { appSettings, currencyList, categoryList, methodList ->
            References(appSettings, currencyList, categoryList, methodList)
        }

        return combine(references, month) { refs, current -> refs to current }
            .flatMapLatest { (refs, current) ->
                // The clock is read inside the flow, so a calendar left open
                // overnight marks the right cell as today when it next recomputes.
                val now = today()
                val range = gridRange(current, refs.settings.firstDayOfWeek)
                transactions.observeRange(range)
                    .map { records -> build(records, refs, current, now, range) }
            }
    }

    private fun build(
        records: List<Transaction>,
        refs: References,
        month: YearMonth,
        now: LocalDate,
        range: DateRange,
    ): MonthCalendar {
        val table = CurrencyTable(refs.currencies, refs.settings.baseCurrencyCode)
        val base = table.base
        val categoryById = refs.categories.associateBy { it.id }
        val methodById = refs.paymentMethods.associateBy { it.id }

        // Records arrive from the repository already newest-first, and grouping
        // preserves encounter order, so each day's list is in display order.
        val itemsByDay = HashMap<LocalDate, MutableList<TransactionDetails>>()
        val incomeByDay = HashMap<LocalDate, Long>()
        val expenseByDay = HashMap<LocalDate, Long>()

        for (record in records) {
            val category = categoryById[record.categoryId]
            itemsByDay.getOrPut(record.date) { ArrayList() } += TransactionDetails(
                transaction = record,
                category = category,
                subcategory = category?.subcategory(record.subcategoryId),
                paymentMethod = methodById[record.paymentMethodId],
                currency = table.currency(record.currencyCode),
            )

            // Identity in practice — every amount is PKR. It goes through the table
            // anyway so a hand-edited file carrying some other code is normalised
            // rather than summed as though it were rupees.
            val inBase = table.toBase(record.money).amountMinor

            if (record.type.isIncome) {
                incomeByDay[record.date] = (incomeByDay[record.date] ?: 0L) + inBase
            } else {
                expenseByDay[record.date] = (expenseByDay[record.date] ?: 0L) + inBase
            }
        }

        val weeks = (0 until range.dayCount).map { offset ->
            range.start.plusDays(offset.toLong())
        }.map { date ->
            CalendarDay(
                date = date,
                inMonth = YearMonth.from(date) == month,
                isToday = date == now,
                incomeMinor = incomeByDay[date] ?: 0L,
                expenseMinor = expenseByDay[date] ?: 0L,
                currencyCode = base.code,
                items = itemsByDay[date].orEmpty(),
            )
        }.chunked(COLUMNS)

        val inMonth = weeks.flatten().filter { it.inMonth }

        var income = 0L
        var expense = 0L
        var count = 0
        for (day in inMonth) {
            income += day.incomeMinor
            expense += day.expenseMinor
            count += day.count
        }

        // Ties go to the earlier day, so "the busiest day" is stable rather than
        // hopping about as equal days are re-derived.
        val busiest = inMonth
            .filter { it.hasExpense }
            .maxWithOrNull(compareBy<CalendarDay> { it.expenseMinor }.thenByDescending { it.date })

        return MonthCalendar(
            month = month,
            today = now,
            weekdays = weekdayOrder(refs.settings.firstDayOfWeek),
            weeks = weeks,
            baseCurrency = base,
            totals = TransactionTotals(
                income = Money(income, base.code),
                expense = Money(expense, base.code),
                count = count,
            ),
            peakExpenseMinor = busiest?.expenseMinor ?: 0L,
            busiestDay = busiest,
            activeDayCount = inMonth.count { it.hasActivity },
        )
    }

    private data class References(
        val settings: AppSettings,
        val currencies: List<Currency>,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
    )

    companion object {
        const val COLUMNS = 7

        /**
         * Every day the grid shows, including the leading and trailing days that fill
         * the first and last rows out to whole weeks.
         *
         * Also what the repository is asked to load, so the figures on those borrowed
         * days are real rather than blank.
         */
        fun gridRange(month: YearMonth, firstDayOfWeek: DayOfWeek): DateRange {
            val first = month.atDay(1)
            // How many days of the previous month sit before the 1st, given where the
            // user's week starts. `floorMod` because the difference goes negative
            // whenever the month starts before the first day of the week.
            val lead = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, COLUMNS)
            val start = first.minusDays(lead.toLong())
            val cells = ((lead + month.lengthOfMonth() + COLUMNS - 1) / COLUMNS) * COLUMNS
            return DateRange(start, start.plusDays((cells - 1).toLong()))
        }

        /** Column headings, in order, for the user's chosen first day of week. */
        fun weekdayOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
            (0 until COLUMNS).map { firstDayOfWeek.plus(it.toLong()) }
    }
}
