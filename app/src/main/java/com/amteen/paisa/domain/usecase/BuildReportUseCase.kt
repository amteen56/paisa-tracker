package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.core.time.PeriodFilter
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
import java.time.LocalDate
import java.time.YearMonth

/** One category's share of the period's spending. Drives the donut and its legend. */
data class CategorySlice(
    val categoryId: String,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val amountMinor: Long,
    val currencyCode: String,
    /** 0f..1f of the period's total expense. Display only — never money. */
    val share: Float,
    val count: Int,
) {
    val amount: Money get() = Money(amountMinor, currencyCode)
}

/** One subcategory's share within a drilled-into category. */
data class SubcategorySlice(
    /** Null is the real "no subcategory chosen" bucket, not a missing value. */
    val subcategoryId: String?,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val share: Float,
    val count: Int,
) {
    val amount: Money get() = Money(amountMinor, currencyCode)
}

/** One day of the period. Zero-filled, so a gap in spending is drawn as a gap. */
data class DailyPoint(
    val date: LocalDate,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val currencyCode: String,
) {
    val expense: Money get() = Money(expenseMinor, currencyCode)
    val income: Money get() = Money(incomeMinor, currencyCode)
}

/** One month of the trend. Zero-filled for the same reason. */
data class MonthlyPoint(
    val month: YearMonth,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val currencyCode: String,
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
    val expense: Money get() = Money(expenseMinor, currencyCode)
    val income: Money get() = Money(incomeMinor, currencyCode)
    val net: Money get() = Money(netMinor, currencyCode)
}

/**
 * Everything a report renders, derived in one pass over one read.
 *
 * Assembled here rather than in the ViewModel so every figure is testable without
 * Android, and so no chart has to sum anything to draw itself — see CLAUDE.md.
 */
data class Report(
    val period: PeriodFilter,
    /** Null only for [PeriodFilter.AllTime], where there is no bounding span. */
    val range: DateRange?,
    val label: String,
    val currency: Currency,

    val totals: TransactionTotals,
    /** Days in the period — the divisor behind [averageExpensePerDayMinor]. */
    val dayCount: Int,
    val averageExpensePerDayMinor: Long,

    val categories: List<CategorySlice>,
    /** The category the user drilled into, if any. */
    val selectedCategoryId: String?,
    val subcategories: List<SubcategorySlice>,

    /**
     * Empty for a period longer than [MAX_DAILY_BARS] days. 365 bars on a phone is
     * one pixel each, which is a picture of nothing — the monthly trend carries a
     * long period instead.
     */
    val dailySeries: List<DailyPoint>,
    val monthlySeries: List<MonthlyPoint>,
    val topExpenses: List<TransactionDetails>,
    val busiestDay: DailyPoint?,

    /**
     * Expense over the same-length window immediately before this one.
     *
     * Null when there is nothing meaningful to compare against — an all-time report,
     * or a period too long to justify loading a second copy of.
     */
    val previousExpenseMinor: Long?,
) {
    val averageExpensePerDay: Money get() = Money(averageExpensePerDayMinor, currency.code)

    val hasAnyTransactions: Boolean get() = totals.count > 0

    val selectedCategory: CategorySlice?
        get() = categories.firstOrNull { it.categoryId == selectedCategoryId }

    /**
     * Change against the preceding window, as a percentage of it.
     *
     * Null when there is no previous spending to divide by: a jump from zero is not
     * "up 100%", it is simply the first period with anything in it.
     */
    val expenseChangePercent: Double?
        get() {
            val previous = previousExpenseMinor ?: return null
            if (previous <= 0L) return null
            val difference = totals.expense.amountMinor - previous
            return difference.toDouble() / previous.toDouble() * 100.0
        }

    companion object {
        /** Beyond this many days the daily chart stops being readable. */
        const val MAX_DAILY_BARS = 62

        /** Months of trend shown when the period itself is shorter than that. */
        const val TREND_MONTHS = 6

        const val TOP_EXPENSE_COUNT = 10
        const val TOP_CATEGORY_COUNT = 8
    }
}

/**
 * The reports screen, derived from the ledger.
 *
 * **What this reads is deliberately bounded.** A report could justify loading every
 * shard the user has ever written; instead the read is the union of three spans —
 * the period itself, six months of trend, and (only for periods of two months or
 * less) the preceding window to compare against. For "this month" that is about
 * seven files; for "this year", twelve. Only `AllTime` reads everything, because
 * that is exactly what the user asked for.
 */
class BuildReportUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    /** What the screen asks for: a period, and optionally a category to drill into. */
    data class Request(
        val period: PeriodFilter = PeriodFilter.ThisMonth,
        val selectedCategoryId: String? = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(request: Flow<Request>): Flow<Report> {
        val references = combine(
            settings.settings,
            currencies.currencies,
            categories.categories,
            paymentMethods.paymentMethods,
        ) { appSettings, currencyList, categoryList, methodList ->
            References(appSettings, currencyList, categoryList, methodList)
        }

        return combine(references, request) { refs, req -> refs to req }
            .flatMapLatest { (refs, req) ->
                // The clock is read inside the flow, so a report left open overnight
                // resolves "this month" against the real date when it recomputes.
                val now = today()
                val range = req.period.resolve(now, refs.settings.firstDayOfWeek)
                val source = if (range == null) {
                    transactions.observeAll()
                } else {
                    transactions.observeRange(readRange(range))
                }
                source.map { records -> build(records, refs, req, range) }
            }
    }

    /**
     * The span actually loaded: the report's own range, widened to cover the trend
     * and the comparison window.
     */
    private fun readRange(range: DateRange): DateRange {
        val trendStart = YearMonth.from(range.endInclusive)
            .minusMonths((Report.TREND_MONTHS - 1).toLong())
            .atDay(1)
        val comparisonStart = comparisonRange(range)?.start
        val start = listOfNotNull(range.start, trendStart, comparisonStart).min()
        return DateRange(start, range.endInclusive)
    }

    /**
     * The same-length window immediately before [range].
     *
     * Null past [Report.MAX_DAILY_BARS] days: doubling the read to compare a whole
     * year against the year before it is a lot of file I/O for one percentage.
     */
    private fun comparisonRange(range: DateRange): DateRange? {
        if (range.dayCount > Report.MAX_DAILY_BARS) return null
        val end = range.start.minusDays(1)
        return DateRange(end.minusDays((range.dayCount - 1).toLong()), end)
    }

    private fun build(
        records: List<Transaction>,
        refs: References,
        request: Request,
        range: DateRange?,
    ): Report {
        val table = CurrencyTable(refs.currencies, refs.settings.baseCurrencyCode)
        val base = table.base
        val categoryById = refs.categories.associateBy { it.id }
        val methodById = refs.paymentMethods.associateBy { it.id }

        // `observeAll` is unbounded and the widened read reaches outside the period,
        // so membership is decided here rather than trusted from the repository.
        val inPeriod = if (range == null) records else records.filter { it.date in range }
        val comparison = range?.let { comparisonRange(it) }

        var income = 0L
        var expense = 0L
        var previousExpense = 0L

        val byCategory = HashMap<String, Bucket>()
        val bySubcategory = HashMap<String?, Bucket>()
        val byDay = HashMap<LocalDate, LongArray>()
        val byMonth = HashMap<YearMonth, LongArray>()

        for (record in records) {
            // Identity in practice — every amount is PKR — but it goes through the
            // table so a hand-edited record with another code is normalised rather
            // than counted as though it were rupees.
            val amount = table.toBase(record.money).amountMinor
            val isIncome = record.type.isIncome

            if (comparison != null && record.date in comparison && !isIncome) {
                previousExpense += amount
            }

            // The trend spans more than the report, so every loaded record feeds the
            // monthly buckets even when it falls outside the period.
            val monthSlot = byMonth.getOrPut(YearMonth.from(record.date)) { LongArray(2) }
            if (isIncome) monthSlot[0] += amount else monthSlot[1] += amount

            if (range != null && record.date !in range) continue

            val daySlot = byDay.getOrPut(record.date) { LongArray(2) }
            if (isIncome) {
                income += amount
                daySlot[0] += amount
                continue
            }

            expense += amount
            daySlot[1] += amount

            byCategory.getOrPut(record.categoryId) { Bucket() }.add(amount)
            if (record.categoryId == request.selectedCategoryId) {
                bySubcategory.getOrPut(record.subcategoryId) { Bucket() }.add(amount)
            }
        }

        val effectiveRange = range ?: inPeriod.dateSpan()
        val dayCount = range?.dayCount ?: (effectiveRange?.dayCount ?: 1)

        return Report(
            period = request.period,
            range = range,
            label = request.period.label,
            currency = base,
            totals = TransactionTotals(
                income = Money(income, base.code),
                expense = Money(expense, base.code),
                // One currency: nothing is converted, so nothing is disclosed.
                mixedCurrency = false,
                count = inPeriod.size,
            ),
            dayCount = dayCount,
            averageExpensePerDayMinor = divideRounded(expense, dayCount),
            categories = slices(byCategory, expense, categoryById, base.code),
            selectedCategoryId = request.selectedCategoryId,
            subcategories = subcategorySlices(
                bySubcategory,
                categoryById[request.selectedCategoryId],
                base.code,
            ),
            dailySeries = dailySeries(byDay, effectiveRange, base.code),
            monthlySeries = monthlySeries(byMonth, effectiveRange, base.code),
            topExpenses = topExpenses(inPeriod, categoryById, methodById, table),
            busiestDay = busiestDay(byDay, base.code),
            previousExpenseMinor = if (comparison == null) null else previousExpense,
        )
    }

    private fun slices(
        buckets: Map<String, Bucket>,
        totalExpense: Long,
        categoryById: Map<String, Category>,
        currencyCode: String,
    ): List<CategorySlice> {
        if (buckets.isEmpty()) return emptyList()

        return buckets.entries
            // Amount descending, then id, so equal categories keep a stable order
            // instead of swapping places between recompositions.
            .sortedWith(
                compareByDescending<Map.Entry<String, Bucket>> { it.value.amount }
                    .thenBy { it.key },
            )
            .take(Report.TOP_CATEGORY_COUNT)
            .map { (id, bucket) ->
                val category = categoryById[id]
                CategorySlice(
                    categoryId = id,
                    name = category?.name ?: "Uncategorised",
                    // A deleted category still needs a colour, or its slice draws
                    // invisible against the ring.
                    colorArgb = category?.colorArgb ?: FALLBACK_CATEGORY_COLOR,
                    iconKey = category?.iconKey.orEmpty(),
                    amountMinor = bucket.amount,
                    currencyCode = currencyCode,
                    share = share(bucket.amount, totalExpense),
                    count = bucket.count,
                )
            }
    }

    private fun subcategorySlices(
        buckets: Map<String?, Bucket>,
        category: Category?,
        currencyCode: String,
    ): List<SubcategorySlice> {
        if (buckets.isEmpty()) return emptyList()
        val total = buckets.values.sumOf { it.amount }

        return buckets.entries
            .sortedWith(
                compareByDescending<Map.Entry<String?, Bucket>> { it.value.amount }
                    .thenBy { it.key ?: "" },
            )
            .map { (id, bucket) ->
                SubcategorySlice(
                    subcategoryId = id,
                    // A null id is a real bucket: spending filed under the category
                    // with no subcategory picked. Naming it beats dropping it and
                    // quietly losing money out of the breakdown.
                    name = id?.let { category?.subcategory(it)?.name ?: "Removed" }
                        ?: "Unspecified",
                    amountMinor = bucket.amount,
                    currencyCode = currencyCode,
                    share = share(bucket.amount, total),
                    count = bucket.count,
                )
            }
    }

    /** Zero-filled across the whole range, so an empty day is drawn, not skipped. */
    private fun dailySeries(
        byDay: Map<LocalDate, LongArray>,
        range: DateRange?,
        currencyCode: String,
    ): List<DailyPoint> {
        if (range == null || range.dayCount > Report.MAX_DAILY_BARS) return emptyList()
        return (0 until range.dayCount).map { offset ->
            val date = range.start.plusDays(offset.toLong())
            val slot = byDay[date]
            DailyPoint(
                date = date,
                expenseMinor = slot?.get(1) ?: 0L,
                incomeMinor = slot?.get(0) ?: 0L,
                currencyCode = currencyCode,
            )
        }
    }

    /**
     * The trend: the months the range covers, or the last [Report.TREND_MONTHS]
     * ending at the range end when the range is shorter than that.
     *
     * A single-month report with a one-point trend line would be a dot, which says
     * nothing about direction — the whole reason to draw a trend.
     */
    private fun monthlySeries(
        byMonth: Map<YearMonth, LongArray>,
        range: DateRange?,
        currencyCode: String,
    ): List<MonthlyPoint> {
        val months = if (range == null) {
            byMonth.keys.sorted()
        } else {
            val last = YearMonth.from(range.endInclusive)
            val spanned = range.months()
            val first = if (spanned.size >= Report.TREND_MONTHS) {
                spanned.first()
            } else {
                last.minusMonths((Report.TREND_MONTHS - 1).toLong())
            }
            buildList {
                var cursor = first
                while (!cursor.isAfter(last)) {
                    add(cursor)
                    cursor = cursor.plusMonths(1)
                }
            }
        }

        return months.map { month ->
            val slot = byMonth[month]
            MonthlyPoint(
                month = month,
                expenseMinor = slot?.get(1) ?: 0L,
                incomeMinor = slot?.get(0) ?: 0L,
                currencyCode = currencyCode,
            )
        }
    }

    private fun topExpenses(
        records: List<Transaction>,
        categoryById: Map<String, Category>,
        methodById: Map<String, PaymentMethod>,
        table: CurrencyTable,
    ): List<TransactionDetails> = records
        .filter { it.type.isExpense }
        .sortedWith(
            compareByDescending<Transaction> { table.toBase(it.money).amountMinor }
                .thenByDescending { it.date }
                .thenBy { it.id },
        )
        .take(Report.TOP_EXPENSE_COUNT)
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

    /** Ties go to the earlier day, so the answer does not hop between redraws. */
    private fun busiestDay(byDay: Map<LocalDate, LongArray>, currencyCode: String): DailyPoint? =
        byDay.entries
            .filter { it.value[1] > 0L }
            .maxWithOrNull(
                compareBy<Map.Entry<LocalDate, LongArray>> { it.value[1] }
                    .thenByDescending { it.key },
            )
            ?.let { (date, slot) ->
                DailyPoint(
                    date = date,
                    expenseMinor = slot[1],
                    incomeMinor = slot[0],
                    currencyCode = currencyCode,
                )
            }

    /** For an all-time report there is no range, so the span comes from the data. */
    private fun List<Transaction>.dateSpan(): DateRange? {
        if (isEmpty()) return null
        return DateRange(minOf { it.date }, maxOf { it.date })
    }

    private fun share(amount: Long, total: Long): Float =
        if (total <= 0L) 0f else (amount.toDouble() / total.toDouble()).toFloat()

    /**
     * Integer division, rounded half-up, entirely in `Long`.
     *
     * Going through `Double` here would be the one place a fraction of a rupee could
     * reach a displayed figure — see CLAUDE.md.
     */
    private fun divideRounded(total: Long, divisor: Int): Long {
        if (divisor <= 0) return 0L
        return (total + divisor / 2) / divisor
    }

    private class Bucket {
        var amount: Long = 0L
        var count: Int = 0

        fun add(value: Long) {
            amount += value
            count++
        }
    }

    private data class References(
        val settings: AppSettings,
        val currencies: List<Currency>,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
    )

    private companion object {
        /** Neutral grey, legible on both themes. */
        const val FALLBACK_CATEGORY_COLOR: Int = 0xFF8E8E93.toInt()
    }
}
