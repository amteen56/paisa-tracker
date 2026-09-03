package com.amteen.paisa.domain.repository

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetAlert
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.YearMonth

/**
 * Repository contracts.
 *
 * These are the boundary the domain is allowed to know about, so nothing here may
 * mention Android, `File`, JSON, or a DTO — see CLAUDE.md. Implementations live in
 * `data/repository/` and delegate to `JsonFileStore`.
 */

interface TransactionRepository {

    /** Emits whenever [month]'s shard changes. Loads the shard on first collection. */
    fun observeMonth(month: YearMonth): Flow<List<Transaction>>

    /** Emits whenever anything in [range] changes, across every shard it spans. */
    fun observeRange(range: DateRange): Flow<List<Transaction>>

    /** Emits every transaction the user has. Loads all shards on first collection. */
    fun observeAll(): Flow<List<Transaction>>

    suspend fun getMonth(month: YearMonth): List<Transaction>

    suspend fun getRange(range: DateRange): List<Transaction>

    suspend fun getAll(): List<Transaction>

    suspend fun getById(id: String): Transaction?

    /**
     * Inserts or updates.
     *
     * When an edit moves a transaction across a month boundary this **removes it
     * from the old shard and adds it to the new one**, writing both. Getting that
     * wrong duplicates the transaction and corrupts two months of totals, so it is
     * covered by a unit test. See CLAUDE.md rule 3.
     */
    suspend fun save(transaction: Transaction)

    /** Saves many at once, grouping writes so each affected shard is written once. */
    suspend fun saveAll(transactions: List<Transaction>)

    suspend fun delete(id: String)

    /** Months that have a shard on disk, newest first. */
    suspend fun availableMonths(): List<YearMonth>

    /** How many transactions reference [categoryId] — gates hard delete. */
    suspend fun countByCategory(categoryId: String): Int

    suspend fun countBySubcategory(subcategoryId: String): Int

    suspend fun countByPaymentMethod(paymentMethodId: String): Int

    suspend fun countByCurrency(currencyCode: String): Int

    /** Replaces the entire store. Used only by import; see CLAUDE.md rule 8. */
    suspend fun replaceAll(transactions: List<Transaction>)
}

interface CategoryRepository {
    val categories: StateFlow<List<Category>>

    suspend fun load()

    suspend fun getById(id: String): Category?

    suspend fun upsert(category: Category)

    /** Marks archived: hides it from pickers while keeping history intact. */
    suspend fun archive(id: String, archived: Boolean = true)

    /** Permitted only at reference count zero — the caller must check first. */
    suspend fun hardDelete(id: String)

    suspend fun reorder(orderedIds: List<String>)

    suspend fun replaceAll(categories: List<Category>)
}

interface BudgetRepository {
    val budgets: StateFlow<List<Budget>>

    suspend fun load()

    suspend fun getById(id: String): Budget?

    suspend fun upsert(budget: Budget)

    suspend fun archive(id: String, archived: Boolean = true)

    suspend fun hardDelete(id: String)

    suspend fun replaceAll(budgets: List<Budget>)
}

interface CurrencyRepository {
    val currencies: StateFlow<List<Currency>>

    suspend fun load()

    suspend fun getByCode(code: String): Currency?

    suspend fun upsert(currency: Currency)

    /** Rebases every rate so [code] becomes 1.0. Transaction amounts are untouched. */
    suspend fun setBaseCurrency(code: String)

    suspend fun archive(code: String, archived: Boolean = true)

    suspend fun hardDelete(code: String)

    suspend fun replaceAll(currencies: List<Currency>)
}

interface PaymentMethodRepository {
    val paymentMethods: StateFlow<List<PaymentMethod>>

    suspend fun load()

    suspend fun getById(id: String): PaymentMethod?

    suspend fun upsert(paymentMethod: PaymentMethod)

    suspend fun archive(id: String, archived: Boolean = true)

    suspend fun hardDelete(id: String)

    suspend fun reorder(orderedIds: List<String>)

    suspend fun replaceAll(paymentMethods: List<PaymentMethod>)
}

/**
 * Which budget alerts have already been shown.
 *
 * Separate from [BudgetRepository] because it is a different kind of thing: budgets
 * are the user's data, this is the app's memory of what it has told them. Nothing is
 * derived from it, and losing the file costs at most one repeated notification.
 */
interface BudgetAlertStateRepository {
    val fired: StateFlow<Set<BudgetAlert>>

    suspend fun load()

    /** Adds [alerts]; already-recorded ones are a no-op. */
    suspend fun record(alerts: Collection<BudgetAlert>)

    /** Drops everything for one budget — called when that budget is deleted. */
    suspend fun forget(budgetId: String)

    /**
     * Drops records for months before [before], so the file cannot grow without
     * bound over years of use.
     */
    suspend fun pruneBefore(before: YearMonth)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun load()

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
