package com.amteen.paisa.di

import android.content.Context
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileBudgetRepositoryImpl
import com.amteen.paisa.data.repository.FileCategoryRepositoryImpl
import com.amteen.paisa.data.repository.FileCurrencyRepositoryImpl
import com.amteen.paisa.data.repository.FilePaymentMethodRepositoryImpl
import com.amteen.paisa.data.repository.FileSettingsRepositoryImpl
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import com.amteen.paisa.domain.usecase.ArchiveCategoryUseCase
import com.amteen.paisa.domain.usecase.ArchivePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.CountCategoryReferencesUseCase
import com.amteen.paisa.domain.usecase.DeleteCategoryUseCase
import com.amteen.paisa.domain.usecase.DeletePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.DeleteTransactionUseCase
import com.amteen.paisa.domain.usecase.GetBudgetStatusUseCase
import com.amteen.paisa.domain.usecase.GetDashboardSummaryUseCase
import com.amteen.paisa.domain.usecase.ReorderCategoriesUseCase
import com.amteen.paisa.domain.usecase.ReorderPaymentMethodsUseCase
import com.amteen.paisa.domain.usecase.SaveCategoryUseCase
import com.amteen.paisa.domain.usecase.SavePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.SetDefaultPaymentMethodUseCase
import com.amteen.paisa.domain.usecase.GetTransactionDetailsUseCase
import com.amteen.paisa.domain.usecase.ObserveTransactionsUseCase
import com.amteen.paisa.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The dependency graph, written by hand.
 *
 * There is no Hilt/Dagger/Koin here on purpose — see CLAUDE.md. The graph is one
 * file store, six repositories and a handful of use cases; a DI framework would add
 * annotation processing to every build to save this one file.
 *
 * Owned by [com.amteen.paisa.PaisaApp] and built once per process.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Everything lives under the app's private files directory, which is not
     * world-readable and is excluded from cloud backup — see
     * `res/xml/data_extraction_rules.xml`.
     */
    private val rootDir: File = File(appContext.filesDir, FilePaths.ROOT)

    val fileStore = JsonFileStore(rootDir)

    // -- Repositories -------------------------------------------------------

    val settingsRepository: SettingsRepository = FileSettingsRepositoryImpl(fileStore)
    val currencyRepository: CurrencyRepository = FileCurrencyRepositoryImpl(fileStore)
    val categoryRepository: CategoryRepository = FileCategoryRepositoryImpl(fileStore)
    val paymentMethodRepository: PaymentMethodRepository = FilePaymentMethodRepositoryImpl(fileStore)
    val budgetRepository: BudgetRepository = FileBudgetRepositoryImpl(fileStore)
    val transactionRepository: TransactionRepository = FileTransactionRepositoryImpl(fileStore)

    // -- Use cases ----------------------------------------------------------

    val observeTransactions = ObserveTransactionsUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
    )

    val saveTransaction = SaveTransactionUseCase(transactionRepository)

    val deleteTransaction = DeleteTransactionUseCase(transactionRepository)

    // Categories and payment methods. Reference counting sits behind its own use
    // case because both the list screen (to choose which dialog to show) and the
    // delete use case (to enforce it) need the same answer.
    val countCategoryReferences = CountCategoryReferencesUseCase(
        transactions = transactionRepository,
        budgets = budgetRepository,
    )

    val saveCategory = SaveCategoryUseCase(
        categories = categoryRepository,
        transactions = transactionRepository,
    )

    val deleteCategory = DeleteCategoryUseCase(
        categories = categoryRepository,
        countReferences = countCategoryReferences,
    )

    val archiveCategory = ArchiveCategoryUseCase(categoryRepository)

    val reorderCategories = ReorderCategoriesUseCase(categoryRepository)

    val savePaymentMethod = SavePaymentMethodUseCase(paymentMethodRepository)

    val deletePaymentMethod = DeletePaymentMethodUseCase(
        paymentMethods = paymentMethodRepository,
        transactions = transactionRepository,
        settings = settingsRepository,
    )

    val archivePaymentMethod = ArchivePaymentMethodUseCase(
        paymentMethods = paymentMethodRepository,
        settings = settingsRepository,
    )

    val reorderPaymentMethods = ReorderPaymentMethodsUseCase(paymentMethodRepository)

    val setDefaultPaymentMethod = SetDefaultPaymentMethodUseCase(settingsRepository)

    // Budget status is its own use case rather than being folded into the dashboard:
    // Phase 6's budget screen needs exactly the same derivation, and two copies of a
    // money calculation is two chances to disagree.
    val getBudgetStatus = GetBudgetStatusUseCase()

    val getDashboardSummary = GetDashboardSummaryUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
        budgets = budgetRepository,
        budgetStatus = getBudgetStatus,
    )

    val getTransactionDetails = GetTransactionDetailsUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
    )

    // -- Startup ------------------------------------------------------------

    /** Lives as long as the process; cancelled only when the process dies. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _ready = MutableStateFlow(false)

    /**
     * False until the reference data has been read off disk.
     *
     * The UI waits on this rather than rendering against empty lists, which would
     * flash "no categories" on every cold start.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Reads the small reference files and seeds them on first run.
     *
     * Transaction shards are deliberately *not* loaded here — they load lazily per
     * month, so startup cost does not grow with history.
     */
    fun initialize() {
        applicationScope.launch {
            fileStore.ensureDirectories()

            // Order matters only in that settings names the base currency, so the
            // currency table is meaningless without it.
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()
            budgetRepository.load()

            if (!settingsRepository.settings.value.initialized) {
                settingsRepository.update {
                    it.copy(
                        initialized = true,
                        baseCurrencyCode = it.baseCurrencyCode.ifBlank {
                            DefaultData.BASE_CURRENCY_CODE
                        },
                    )
                }
            }

            _ready.value = true
        }
    }
}
