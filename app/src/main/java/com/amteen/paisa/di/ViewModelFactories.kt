package com.amteen.paisa.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.ui.screen.backup.BackupViewModel
import com.amteen.paisa.ui.screen.budget.BudgetEditViewModel
import com.amteen.paisa.ui.screen.budget.BudgetListViewModel
import com.amteen.paisa.ui.screen.calendar.CalendarViewModel
import com.amteen.paisa.ui.screen.category.CategoryEditViewModel
import com.amteen.paisa.ui.screen.category.CategoryListViewModel
import com.amteen.paisa.ui.screen.history.TransactionHistoryViewModel
import com.amteen.paisa.ui.screen.home.HomeViewModel
import com.amteen.paisa.ui.screen.paymentmethod.PaymentMethodViewModel
import com.amteen.paisa.ui.screen.reports.ReportsViewModel
import com.amteen.paisa.ui.screen.settings.SettingsViewModel
import com.amteen.paisa.ui.screen.transaction.AddEditTransactionViewModel
import com.amteen.paisa.ui.screen.transaction.TransactionDetailViewModel
import java.time.LocalDate

/**
 * ViewModel construction, by hand.
 *
 * Each factory names the exact dependencies its ViewModel needs rather than handing
 * over the whole [AppContainer]. That keeps the ViewModel honest about its surface
 * area — and constructible in a test without building a graph.
 *
 * Route arguments are passed as constructor parameters instead of being read from a
 * `SavedStateHandle`, so the ViewModel has no idea it is behind a navigation route
 * and can be exercised directly.
 */
object ViewModelFactories {

    fun addEditTransaction(
        container: AppContainer,
        transactionId: String?,
        type: TransactionType,
        date: LocalDate? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AddEditTransactionViewModel(
                transactionId = transactionId,
                initialType = type,
                initialDate = date,
                saveTransaction = container.saveTransaction,
                getTransactionDetails = container.getTransactionDetails,
                categoryRepository = container.categoryRepository,
                paymentMethodRepository = container.paymentMethodRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
                onSaved = container::checkBudgetAlerts,
            )
        }
    }

    fun transactionDetail(
        container: AppContainer,
        transactionId: String,
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            TransactionDetailViewModel(
                transactionId = transactionId,
                getTransactionDetails = container.getTransactionDetails,
                deleteTransaction = container.deleteTransaction,
            )
        }
    }

    fun categoryList(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CategoryListViewModel(
                categoryRepository = container.categoryRepository,
                countReferences = container.countCategoryReferences,
                deleteCategory = container.deleteCategory,
                archiveCategory = container.archiveCategory,
                reorderCategories = container.reorderCategories,
            )
        }
    }

    fun categoryEdit(
        container: AppContainer,
        categoryId: String?,
        initialScope: CategoryScope,
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CategoryEditViewModel(
                categoryId = categoryId,
                initialScope = initialScope,
                categoryRepository = container.categoryRepository,
                saveCategory = container.saveCategory,
            )
        }
    }

    fun paymentMethods(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            PaymentMethodViewModel(
                paymentMethodRepository = container.paymentMethodRepository,
                settingsRepository = container.settingsRepository,
                transactionRepository = container.transactionRepository,
                savePaymentMethod = container.savePaymentMethod,
                deletePaymentMethod = container.deletePaymentMethod,
                archivePaymentMethod = container.archivePaymentMethod,
                reorderPaymentMethods = container.reorderPaymentMethods,
                setDefaultPaymentMethod = container.setDefaultPaymentMethod,
            )
        }
    }

    fun budgetList(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BudgetListViewModel(
                budgetRepository = container.budgetRepository,
                transactionRepository = container.transactionRepository,
                categoryRepository = container.categoryRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
                budgetStatus = container.getBudgetStatus,
                archiveBudget = container.archiveBudget,
                deleteBudget = container.deleteBudget,
                reorderBudgets = container.reorderBudgets,
            )
        }
    }

    fun budgetEdit(
        container: AppContainer,
        budgetId: String?,
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BudgetEditViewModel(
                budgetId = budgetId,
                budgetRepository = container.budgetRepository,
                categoryRepository = container.categoryRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
                saveBudget = container.saveBudget,
                getBudgetHistory = container.getBudgetHistory,
            )
        }
    }

    fun calendar(
        container: AppContainer,
        initialDate: LocalDate? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CalendarViewModel(
                getMonthCalendar = container.getMonthCalendar,
                categoryRepository = container.categoryRepository,
                paymentMethodRepository = container.paymentMethodRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
                initialDate = initialDate,
            )
        }
    }

    fun backup(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BackupViewModel(
                exportBackup = container.exportBackup,
                exportCsv = container.exportCsv,
                prepareImport = container.prepareImport,
                commitImport = container.commitImport,
                writeLocalBackup = container.writeLocalBackup,
                backups = container.backupRepository,
            )
        }
    }

    fun reports(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ReportsViewModel(
                buildReport = container.buildReport,
                categoryRepository = container.categoryRepository,
                paymentMethodRepository = container.paymentMethodRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
            )
        }
    }

    fun settings(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                seedSampleData = container.seedSampleData,
                clearSampleData = container.clearSampleData,
            )
        }
    }

    fun home(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                getDashboardSummary = container.getDashboardSummary,
                categoryRepository = container.categoryRepository,
                paymentMethodRepository = container.paymentMethodRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
                budgetRepository = container.budgetRepository,
            )
        }
    }

    fun transactionHistory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            TransactionHistoryViewModel(
                observeTransactions = container.observeTransactions,
                categoryRepository = container.categoryRepository,
                paymentMethodRepository = container.paymentMethodRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
            )
        }
    }
}
