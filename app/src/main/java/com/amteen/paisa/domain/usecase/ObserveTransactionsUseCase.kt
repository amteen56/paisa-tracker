package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.money.Money
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.model.TransactionQuery
import com.amteen.paisa.domain.model.TransactionSort
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

/**
 * The transaction list, filtered, sorted, grouped and totalled.
 *
 * All of this lives here rather than in the ViewModel or the list composable so it
 * is testable without Android and identical wherever a filtered list is shown —
 * history, search, a category drill-down, a calendar day.
 */
class ObserveTransactionsUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(query: Flow<TransactionQuery>): Flow<TransactionListResult> {
        val references = combine(
            settings.settings,
            currencies.currencies,
            categories.categories,
            paymentMethods.paymentMethods,
        ) { appSettings, currencyList, categoryList, methodList ->
            References(appSettings, currencyList, categoryList, methodList)
        }

        return combine(references, query) { refs, q -> refs to q }
            .flatMapLatest { (refs, q) ->
                val now = today()
                val range = q.period.resolve(now, refs.settings.firstDayOfWeek)
                val source = if (range == null) {
                    transactions.observeAll()
                } else {
                    transactions.observeRange(range)
                }
                source.map { records -> build(records, q, refs, now) }
            }
    }

    private fun build(
        records: List<Transaction>,
        query: TransactionQuery,
        refs: References,
        now: LocalDate,
    ): TransactionListResult {
        val table = CurrencyTable(refs.currencies, refs.settings.baseCurrencyCode)
        val categoryById = refs.categories.associateBy { it.id }
        val methodById = refs.paymentMethods.associateBy { it.id }

        val resolved = records.map { record ->
            val category = categoryById[record.categoryId]
            TransactionDetails(
                transaction = record,
                category = category,
                subcategory = category?.subcategory(record.subcategoryId),
                paymentMethod = methodById[record.paymentMethodId],
                currency = table.currency(record.currencyCode),
            )
        }

        val filtered = resolved.filter { matches(it, query, table) }
        val sorted = sort(filtered, query.sort, table)

        return TransactionListResult(
            items = sorted,
            sections = section(sorted, query.sort, table, now),
            totals = totals(sorted, table),
            currencyTable = table,
            settings = refs.settings,
        )
    }

    private fun matches(
        details: TransactionDetails,
        query: TransactionQuery,
        table: CurrencyTable,
    ): Boolean {
        val record = details.transaction

        if (query.types.isNotEmpty() && record.type !in query.types) return false
        if (query.categoryIds.isNotEmpty() && record.categoryId !in query.categoryIds) return false
        if (query.subcategoryIds.isNotEmpty() && record.subcategoryId !in query.subcategoryIds) return false
        if (query.paymentMethodIds.isNotEmpty() &&
            record.paymentMethodId !in query.paymentMethodIds
        ) {
            return false
        }

        if (query.minAmountMinorBase != null || query.maxAmountMinorBase != null) {
            // Compare in base currency, or a 50 USD expense would be excluded by a
            // "under Rs. 1000" filter on the strength of the number 50 alone.
            val inBase = table.toBase(record.money).amountMinor
            query.minAmountMinorBase?.let { if (inBase < it) return false }
            query.maxAmountMinorBase?.let { if (inBase > it) return false }
        }

        if (query.text.isNotBlank()) {
            val needle = query.text.trim()
            val haystack = sequenceOf(
                record.description,
                record.notes.orEmpty(),
                details.category?.name.orEmpty(),
                details.subcategory?.name.orEmpty(),
                details.paymentMethod?.name.orEmpty(),
            )
            if (haystack.none { it.contains(needle, ignoreCase = true) }) return false
        }

        return true
    }

    private fun sort(
        items: List<TransactionDetails>,
        order: TransactionSort,
        table: CurrencyTable,
    ): List<TransactionDetails> = when (order) {
        TransactionSort.NEWEST_FIRST -> items.sortedWith(
            compareByDescending<TransactionDetails> { it.transaction.date }
                .thenByDescending { it.transaction.time }
                .thenBy { it.id },
        )
        TransactionSort.OLDEST_FIRST -> items.sortedWith(
            compareBy<TransactionDetails> { it.transaction.date }
                .thenBy { it.transaction.time }
                .thenBy { it.id },
        )
        // Amount sorts convert first, so a mixed-currency list orders by real value.
        TransactionSort.AMOUNT_HIGH_FIRST -> items.sortedWith(
            compareByDescending<TransactionDetails> { table.toBase(it.money).amountMinor }
                .thenBy { it.id },
        )
        TransactionSort.AMOUNT_LOW_FIRST -> items.sortedWith(
            compareBy<TransactionDetails> { table.toBase(it.money).amountMinor }
                .thenBy { it.id },
        )
    }

    /**
     * Date-grouped sections for sticky headers.
     *
     * Only meaningful for the date sorts — grouping an amount-sorted list by day
     * would scatter one-item headers down the screen — so the amount sorts return a
     * single unlabelled section instead.
     */
    private fun section(
        items: List<TransactionDetails>,
        order: TransactionSort,
        table: CurrencyTable,
        now: LocalDate,
    ): List<TransactionSection> {
        if (items.isEmpty()) return emptyList()
        if (order == TransactionSort.AMOUNT_HIGH_FIRST || order == TransactionSort.AMOUNT_LOW_FIRST) {
            return listOf(
                TransactionSection(
                    date = null,
                    label = "",
                    items = items,
                    netMinor = 0L,
                    currencyCode = table.base.code,
                ),
            )
        }

        return items.groupBy { it.transaction.date }
            .map { (date, dayItems) ->
                var net = 0L
                for (item in dayItems) {
                    val inBase = table.toBase(item.money).amountMinor
                    net += if (item.transaction.type.isIncome) inBase else -inBase
                }
                TransactionSection(
                    date = date,
                    label = DateFormatters.listHeader(date, now),
                    items = dayItems,
                    netMinor = net,
                    currencyCode = table.base.code,
                )
            }
            // groupBy preserves encounter order, which is already the sort order.
            .toList()
    }

    private fun totals(items: List<TransactionDetails>, table: CurrencyTable): TransactionTotals {
        var income = 0L
        var expense = 0L
        var mixed = false
        for (item in items) {
            if (item.transaction.currencyCode != table.base.code) mixed = true
            val inBase = table.toBase(item.money).amountMinor
            if (item.transaction.type.isIncome) income += inBase else expense += inBase
        }
        return TransactionTotals(
            income = Money(income, table.base.code),
            expense = Money(expense, table.base.code),
            mixedCurrency = mixed,
            count = items.size,
        )
    }

    private data class References(
        val settings: AppSettings,
        val currencies: List<Currency>,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
    )
}

/** One day's worth of transactions, with the day's net in the base currency. */
data class TransactionSection(
    val date: LocalDate?,
    val label: String,
    val items: List<TransactionDetails>,
    val netMinor: Long,
    val currencyCode: String,
) {
    val net: Money get() = Money(netMinor, currencyCode)
}

data class TransactionListResult(
    val items: List<TransactionDetails>,
    val sections: List<TransactionSection>,
    val totals: TransactionTotals,
    val currencyTable: CurrencyTable,
    val settings: AppSettings,
)
