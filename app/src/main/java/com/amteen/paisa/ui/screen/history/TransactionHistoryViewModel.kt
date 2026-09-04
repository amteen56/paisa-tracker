package com.amteen.paisa.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.time.PeriodFilter
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.TransactionQuery
import com.amteen.paisa.domain.model.TransactionSort
import com.amteen.paisa.domain.model.TransactionTotals
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.usecase.ObserveTransactionsUseCase
import com.amteen.paisa.domain.usecase.TransactionSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionHistoryUiState(
    val isLoading: Boolean = true,
    val query: TransactionQuery = TransactionQuery(),
    val sections: List<TransactionSection> = emptyList(),
    val totals: TransactionTotals = TransactionTotals.empty("PKR"),
    val baseCurrency: Currency = CurrencyTable.fallback("PKR"),

    /** Reference lists for the filter sheet. */
    val categories: List<Category> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),

    val searchVisible: Boolean = false,
    val filterSheetVisible: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

sealed interface TransactionHistoryEvent {
    data class SearchChanged(val text: String) : TransactionHistoryEvent
    data class PeriodSelected(val period: PeriodFilter) : TransactionHistoryEvent
    data class SortSelected(val sort: TransactionSort) : TransactionHistoryEvent
    data class TypeToggled(val type: TransactionType) : TransactionHistoryEvent
    data class CategoryToggled(val id: String) : TransactionHistoryEvent
    data class PaymentMethodToggled(val id: String) : TransactionHistoryEvent

    data object ToggleSearch : TransactionHistoryEvent
    data object OpenFilters : TransactionHistoryEvent
    data object DismissFilters : TransactionHistoryEvent
    data object ClearFilters : TransactionHistoryEvent
}

/**
 * The transaction history.
 *
 * The query is the single source of truth: everything shown is derived from it by
 * [ObserveTransactionsUseCase], so filtering, sorting, grouping and the totals can
 * never disagree with each other. The ViewModel only edits the query and holds the
 * two pieces of purely visual state (whether the search field and filter sheet are
 * open).
 */
class TransactionHistoryViewModel(
    observeTransactions: ObserveTransactionsUseCase,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow(TransactionQuery())
    private val chrome = MutableStateFlow(Chrome())

    val uiState: StateFlow<TransactionHistoryUiState> = combine(
        query,
        observeTransactions(query),
        categoryRepository.categories,
        paymentMethodRepository.paymentMethods,
        chrome,
    ) { currentQuery, result, categories, methods, flags ->
        TransactionHistoryUiState(
            isLoading = false,
            query = currentQuery,
            sections = result.sections,
            totals = result.totals,
            baseCurrency = result.currencyTable.base,
            categories = categories.filterNot { it.archived },
            paymentMethods = methods.filterNot { it.archived },
            searchVisible = flags.searchVisible,
            filterSheetVisible = flags.filterSheetVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        // Kept alive briefly across a configuration change so rotating the device
        // does not re-read shards and re-derive every total.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionHistoryUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()

            // Honour the user's preferred sort order as the starting point.
            val defaultSort = TransactionSort.from(settingsRepository.settings.value.defaultSortOrder)
            query.update { it.copy(sort = defaultSort) }
        }
    }

    fun onEvent(event: TransactionHistoryEvent) {
        when (event) {
            is TransactionHistoryEvent.SearchChanged ->
                query.update { it.copy(text = event.text) }

            is TransactionHistoryEvent.PeriodSelected ->
                query.update { it.copy(period = event.period) }

            is TransactionHistoryEvent.SortSelected ->
                query.update { it.copy(sort = event.sort) }

            is TransactionHistoryEvent.TypeToggled ->
                query.update { it.copy(types = it.types.toggle(event.type)) }

            is TransactionHistoryEvent.CategoryToggled ->
                query.update {
                    val next = it.categoryIds.toggle(event.id)
                    // Dropping a category must drop the subcategories chosen under
                    // it, or the query keeps filtering on something invisible.
                    val validSubcategories = categoryRepository.categories.value
                        .filter { category -> category.id in next }
                        .flatMap { category -> category.subcategories.map { sub -> sub.id } }
                        .toSet()
                    it.copy(
                        categoryIds = next,
                        subcategoryIds = it.subcategoryIds.intersect(validSubcategories),
                    )
                }

            is TransactionHistoryEvent.PaymentMethodToggled ->
                query.update { it.copy(paymentMethodIds = it.paymentMethodIds.toggle(event.id)) }


            TransactionHistoryEvent.ToggleSearch -> {
                val closing = chrome.value.searchVisible
                chrome.update { it.copy(searchVisible = !it.searchVisible) }
                // Closing the search field clears the term, so a hidden filter is
                // never silently narrowing the list.
                if (closing) query.update { it.copy(text = "") }
            }

            TransactionHistoryEvent.OpenFilters ->
                chrome.update { it.copy(filterSheetVisible = true) }

            TransactionHistoryEvent.DismissFilters ->
                chrome.update { it.copy(filterSheetVisible = false) }

            TransactionHistoryEvent.ClearFilters ->
                query.update {
                    // Period and sort are not filters; clearing them would fight
                    // the user's explicit choice of what they are looking at.
                    TransactionQuery(text = it.text, period = it.period, sort = it.sort)
                }
        }
    }

    private data class Chrome(
        val searchVisible: Boolean = false,
        val filterSheetVisible: Boolean = false,
    )

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (contains(value)) this - value else this + value
}
