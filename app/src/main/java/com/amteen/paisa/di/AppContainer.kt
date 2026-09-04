package com.amteen.paisa.di

import android.content.Context
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileBackupRepositoryImpl
import com.amteen.paisa.data.repository.FileBudgetAlertStateRepositoryImpl
import com.amteen.paisa.data.repository.FileBudgetRepositoryImpl
import com.amteen.paisa.data.repository.FileCategoryRepositoryImpl
import com.amteen.paisa.data.repository.FileCurrencyRepositoryImpl
import com.amteen.paisa.data.repository.FilePaymentMethodRepositoryImpl
import com.amteen.paisa.data.repository.FileSettingsRepositoryImpl
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.repository.BackupRepository
import com.amteen.paisa.domain.repository.BudgetAlertStateRepository
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import com.amteen.paisa.domain.usecase.ArchiveBudgetUseCase
import com.amteen.paisa.domain.usecase.ArchiveCategoryUseCase
import com.amteen.paisa.domain.usecase.ArchivePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.CountCategoryReferencesUseCase
import com.amteen.paisa.domain.usecase.DeleteBudgetUseCase
import com.amteen.paisa.domain.usecase.DeleteCategoryUseCase
import com.amteen.paisa.domain.usecase.DeletePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.DeleteTransactionUseCase
import com.amteen.paisa.domain.usecase.EvaluateBudgetAlertsUseCase
import com.amteen.paisa.domain.usecase.GetBudgetHistoryUseCase
import com.amteen.paisa.domain.usecase.GetBudgetStatusUseCase
import com.amteen.paisa.domain.usecase.GetDashboardSummaryUseCase
import com.amteen.paisa.domain.usecase.BuildReportUseCase
import com.amteen.paisa.domain.usecase.ClearSampleDataUseCase
import com.amteen.paisa.domain.usecase.SeedSampleDataUseCase
import com.amteen.paisa.domain.usecase.CommitImportUseCase
import com.amteen.paisa.domain.usecase.ExportBackupUseCase
import com.amteen.paisa.domain.usecase.ExportCsvUseCase
import com.amteen.paisa.domain.usecase.PrepareImportUseCase
import com.amteen.paisa.domain.usecase.WriteLocalBackupUseCase
import com.amteen.paisa.domain.usecase.GetMonthCalendarUseCase
import com.amteen.paisa.domain.usecase.SaveBudgetUseCase
import com.amteen.paisa.notification.BudgetAlertNotifier
import com.amteen.paisa.notification.NotificationChannels
import com.amteen.paisa.domain.usecase.ReorderBudgetsUseCase
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
    val budgetAlertStateRepository: BudgetAlertStateRepository =
        FileBudgetAlertStateRepositoryImpl(fileStore)
    val transactionRepository: TransactionRepository = FileTransactionRepositoryImpl(fileStore)

    /**
     * Whole-app documents and the rolling snapshots. The one repository that is about
     * files-as-documents rather than a collection of records.
     */
    val backupRepository: BackupRepository = FileBackupRepositoryImpl(fileStore)

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

    val saveBudget = SaveBudgetUseCase(
        budgets = budgetRepository,
        categories = categoryRepository,
    )

    val archiveBudget = ArchiveBudgetUseCase(budgetRepository)

    val reorderBudgets = ReorderBudgetsUseCase(budgetRepository)

    val deleteBudget = DeleteBudgetUseCase(
        budgets = budgetRepository,
        alerts = budgetAlertStateRepository,
    )

    val getBudgetHistory = GetBudgetHistoryUseCase(
        budgets = budgetRepository,
        transactions = transactionRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
        budgetStatus = getBudgetStatus,
    )

    val evaluateBudgetAlerts = EvaluateBudgetAlertsUseCase(
        budgets = budgetRepository,
        transactions = transactionRepository,
        categories = categoryRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
        alertState = budgetAlertStateRepository,
        budgetStatus = getBudgetStatus,
    )

    /**
     * Shows the alerts the use case decides on.
     *
     * Held here rather than created per call site because it owns a [Context] and a
     * notification channel; the decision half is pure and lives in the use case.
     */
    val budgetAlertNotifier = BudgetAlertNotifier(appContext, evaluateBudgetAlerts)

    // The calendar needs whole weeks of seven with the neighbouring months filling
    // the gaps, and income and expense kept apart per day — a different shape from
    // the flat day sections `observeTransactions` produces, so it derives its own.
    val getMonthCalendar = GetMonthCalendarUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
    )

    // Reports read a bounded union of the period, six months of trend and the
    // comparison window, rather than every shard ever written. See the use case.
    val buildReport = BuildReportUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        currencies = currencyRepository,
        settings = settingsRepository,
    )

    // Import and export. Validation is a use case rather than a screen concern
    // because "what would this file do" is the half worth testing off-device.
    val exportBackup = ExportBackupUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        budgets = budgetRepository,
        settings = settingsRepository,
        backups = backupRepository,
    )

    val exportCsv = ExportCsvUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        backups = backupRepository,
    )

    val prepareImport = PrepareImportUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        budgets = budgetRepository,
        settings = settingsRepository,
        backups = backupRepository,
    )

    val commitImport = CommitImportUseCase(
        transactions = transactionRepository,
        categories = categoryRepository,
        paymentMethods = paymentMethodRepository,
        budgets = budgetRepository,
        settings = settingsRepository,
        backups = backupRepository,
        exportBackup = exportBackup,
    )

    val writeLocalBackup = WriteLocalBackupUseCase(
        exportBackup = exportBackup,
        backups = backupRepository,
        settings = settingsRepository,
    )

    // Sample data. Merges in and takes only its own records back out, matched on
    // an id prefix, so trying it out is never a one-way door.
    val seedSampleData = SeedSampleDataUseCase(
        transactions = transactionRepository,
        budgets = budgetRepository,
    )

    val clearSampleData = ClearSampleDataUseCase(
        transactions = transactionRepository,
        budgets = budgetRepository,
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
        // Idempotent, and cheap enough to do on every start. Doing it here rather
        // than lazily means the channel exists in system settings before the first
        // alert, so the user can find and tune it in advance.
        NotificationChannels.ensure(appContext)

        applicationScope.launch {
            fileStore.ensureDirectories()

            // Order matters only in that settings names the base currency, so the
            // currency table is meaningless without it.
            settingsRepository.load()
            currencyRepository.load()
            categoryRepository.load()
            paymentMethodRepository.load()
            budgetRepository.load()
            budgetAlertStateRepository.load()

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

            // Catches a budget crossed by an edit made in a previous session, or by
            // the month simply turning over. Runs after `ready` so it never delays
            // the first frame.
            checkBudgetAlerts()
        }
    }

    /**
     * Evaluates the budgets and shows anything newly crossed.
     *
     * Called on start and after a transaction is saved rather than on a schedule:
     * spending only changes when the user records something, so a background worker
     * would be a dependency and a wakeup budget spent to learn nothing. Failures are
     * swallowed — a missed notification must never take down the save that triggered
     * it.
     */
    fun checkBudgetAlerts() {
        applicationScope.launch {
            try {
                budgetAlertNotifier.check()
            } catch (e: Exception) {
                // Deliberately silent. There is no crash reporter in this app by
                // design, and there is nothing the user could do with the failure.
            }
        }
    }
}
