package com.amteen.paisa.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.ui.screen.category.CategoryEditViewModel
import com.amteen.paisa.ui.screen.category.CategoryListViewModel
import com.amteen.paisa.ui.screen.history.TransactionHistoryViewModel
import com.amteen.paisa.ui.screen.home.HomeViewModel
import com.amteen.paisa.ui.screen.paymentmethod.PaymentMethodViewModel
import com.amteen.paisa.ui.screen.transaction.AddEditTransactionViewModel
import com.amteen.paisa.ui.screen.transaction.TransactionDetailViewModel

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
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AddEditTransactionViewModel(
                transactionId = transactionId,
                initialType = type,
                saveTransaction = container.saveTransaction,
                getTransactionDetails = container.getTransactionDetails,
                categoryRepository = container.categoryRepository,
                paymentMethodRepository = container.paymentMethodRepository,
                currencyRepository = container.currencyRepository,
                settingsRepository = container.settingsRepository,
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
