package com.amteen.paisa.testing

import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetAlert
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.repository.BudgetAlertStateRepository
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/**
 * In-memory reference repositories for use-case tests.
 *
 * Only the small single-file repositories are faked. Transactions deliberately use
 * the real [com.amteen.paisa.data.repository.FileTransactionRepositoryImpl] against
 * a temp directory, because the month-shard behaviour *is* the thing under test in
 * anything involving dates — a fake would happily hide the bug that matters.
 */
class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val state = MutableStateFlow(initial)
    override val categories: StateFlow<List<Category>> = state.asStateFlow()

    override suspend fun load() = Unit
    override suspend fun getById(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(category: Category) {
        state.value = state.value.filterNot { it.id == category.id } + category
    }
    override suspend fun archive(id: String, archived: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }
    override suspend fun hardDelete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun reorder(orderedIds: List<String>) = Unit
    override suspend fun replaceAll(categories: List<Category>) {
        state.value = categories
    }
}

class FakePaymentMethodRepository(
    initial: List<PaymentMethod> = emptyList(),
) : PaymentMethodRepository {
    private val state = MutableStateFlow(initial)
    override val paymentMethods: StateFlow<List<PaymentMethod>> = state.asStateFlow()

    override suspend fun load() = Unit
    override suspend fun getById(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(paymentMethod: PaymentMethod) {
        state.value = state.value.filterNot { it.id == paymentMethod.id } + paymentMethod
    }
    override suspend fun archive(id: String, archived: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }
    override suspend fun hardDelete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun reorder(orderedIds: List<String>) = Unit
    override suspend fun replaceAll(paymentMethods: List<PaymentMethod>) {
        state.value = paymentMethods
    }
}

/**
 * Read-only, like the real thing: the app is PKR-only and nothing can add a
 * currency, so there is nothing here to mutate.
 */
class FakeCurrencyRepository(initial: List<Currency> = emptyList()) : CurrencyRepository {
    private val state = MutableStateFlow(initial)
    override val currencies: StateFlow<List<Currency>> = state.asStateFlow()

    override suspend fun load() = Unit
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun load() = Unit
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

class FakeBudgetAlertStateRepository(
    initial: Set<BudgetAlert> = emptySet(),
) : BudgetAlertStateRepository {
    private val state = MutableStateFlow(initial)
    override val fired: StateFlow<Set<BudgetAlert>> = state.asStateFlow()

    override suspend fun load() = Unit
    override suspend fun record(alerts: Collection<BudgetAlert>) {
        state.value = state.value + alerts
    }
    override suspend fun forget(budgetId: String) {
        state.value = state.value.filterNot { it.budgetId == budgetId }.toSet()
    }
    override suspend fun pruneBefore(before: YearMonth) {
        state.value = state.value.filterNot { it.period < before }.toSet()
    }
}

class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    private val state = MutableStateFlow(initial)
    override val budgets: StateFlow<List<Budget>> = state.asStateFlow()

    override suspend fun load() = Unit
    override suspend fun getById(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(budget: Budget) {
        state.value = state.value.filterNot { it.id == budget.id } + budget
    }
    override suspend fun archive(id: String, archived: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }
    override suspend fun hardDelete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
    override suspend fun replaceAll(budgets: List<Budget>) {
        state.value = budgets
    }
}
