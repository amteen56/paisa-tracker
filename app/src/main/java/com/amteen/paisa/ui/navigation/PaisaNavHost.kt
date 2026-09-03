package com.amteen.paisa.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amteen.paisa.R
import com.amteen.paisa.di.LocalAppContainer
import com.amteen.paisa.di.ViewModelFactories
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.ui.components.PlaceholderScreen
import com.amteen.paisa.ui.screen.category.CategoryEditScreen
import com.amteen.paisa.ui.screen.category.CategoryEditViewModel
import com.amteen.paisa.ui.screen.category.CategoryListScreen
import com.amteen.paisa.ui.screen.category.CategoryListViewModel
import com.amteen.paisa.ui.screen.history.TransactionHistoryScreen
import com.amteen.paisa.ui.screen.history.TransactionHistoryViewModel
import com.amteen.paisa.ui.screen.home.HomeScreen
import com.amteen.paisa.ui.screen.home.HomeViewModel
import com.amteen.paisa.ui.screen.more.MoreScreen
import com.amteen.paisa.ui.screen.paymentmethod.PaymentMethodScreen
import com.amteen.paisa.ui.screen.paymentmethod.PaymentMethodViewModel
import com.amteen.paisa.ui.screen.transaction.AddEditTransactionScreen
import com.amteen.paisa.ui.screen.transaction.AddEditTransactionViewModel
import com.amteen.paisa.ui.screen.transaction.TransactionDetailScreen
import com.amteen.paisa.ui.screen.transaction.TransactionDetailViewModel

@Composable
fun PaisaNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBars = remember(currentRoute) { isTopLevelRoute(currentRoute) }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                PaisaBottomBar(currentRoute = currentRoute, navController = navController)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showBars,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        navController.navigate(
                            Routes.addTransaction(TransactionTypeArg.EXPENSE),
                        )
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_transaction)) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // Only the bottom inset is consumed here. This Scaffold contributes no
            // top bar — every screen brings its own, and handles its own status-bar
            // inset — but it does own the bottom navigation, and without this the
            // bar sits on top of the last row of whatever list is showing.
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            // --- Bottom navigation ---------------------------------------
            composable(Routes.HOME) {
                val container = LocalAppContainer.current
                val viewModel: HomeViewModel = viewModel(
                    factory = ViewModelFactories.home(container),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                HomeScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onAddExpense = {
                        navController.navigate(Routes.addTransaction(TransactionTypeArg.EXPENSE))
                    },
                    onAddIncome = {
                        navController.navigate(Routes.addTransaction(TransactionTypeArg.INCOME))
                    },
                    onSeeAllTransactions = {
                        // A tab switch, not a push: the transactions list is a
                        // top-level destination, and pushing it would leave Home
                        // underneath for the back button to fall into.
                        navController.navigate(Routes.TRANSACTIONS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onTransactionClick = { navController.navigate(Routes.transactionDetail(it)) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onBudgets = { navController.navigate(Routes.BUDGETS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                val container = LocalAppContainer.current
                val viewModel: TransactionHistoryViewModel = viewModel(
                    factory = ViewModelFactories.transactionHistory(container),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                TransactionHistoryScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onTransactionClick = { navController.navigate(Routes.transactionDetail(it)) },
                    onAddTransaction = {
                        navController.navigate(Routes.addTransaction(TransactionTypeArg.EXPENSE))
                    },
                )
            }
            composable(Routes.REPORTS) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_reports),
                    phaseNote = "Charts and reports arrive in Phase 8.",
                )
            }
            composable(Routes.MORE) {
                MoreScreen(onNavigate = navController::navigate)
            }

            // --- Transactions ---------------------------------------------
            composable(
                route = Routes.ADD_TRANSACTION_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
                ),
            ) { entry ->
                val container = LocalAppContainer.current
                val type = if (entry.arguments?.getString(Routes.ARG_TYPE) == TransactionTypeArg.INCOME) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
                val viewModel: AddEditTransactionViewModel = viewModel(
                    factory = ViewModelFactories.addEditTransaction(container, null, type),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                AddEditTransactionScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBack = navController::popBackStack,
                )
            }

            composable(
                route = Routes.TRANSACTION_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val container = LocalAppContainer.current
                val id = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
                val viewModel: TransactionDetailViewModel = viewModel(
                    factory = ViewModelFactories.transactionDetail(container, id),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Returning from the edit screen must show the new values. A reload
                // on resume is cheaper than holding a flow open for one record.
                LifecycleResumeEffect(id) {
                    viewModel.load()
                    onPauseOrDispose { }
                }

                TransactionDetailScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(Routes.editTransaction(it)) },
                )
            }

            composable(
                route = Routes.EDIT_TRANSACTION_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val container = LocalAppContainer.current
                val id = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
                val viewModel: AddEditTransactionViewModel = viewModel(
                    factory = ViewModelFactories.addEditTransaction(
                        container = container,
                        transactionId = id,
                        // Overridden by the loaded record; only a fallback for the
                        // brief moment before it arrives.
                        type = TransactionType.EXPENSE,
                    ),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                AddEditTransactionScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBack = navController::popBackStack,
                )
            }

            // --- Categories ------------------------------------------------
            composable(Routes.CATEGORIES) {
                val container = LocalAppContainer.current
                val viewModel: CategoryListViewModel = viewModel(
                    factory = ViewModelFactories.categoryList(container),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                CategoryListScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onAddCategory = {
                        // Carry the current tab through, so the new category starts
                        // out applying to the type the user was looking at.
                        navController.navigate(Routes.categoryEdit(scope = state.scope.name))
                    },
                    onEditCategory = { navController.navigate(Routes.categoryEdit(id = it)) },
                    onBack = navController::popBackStack,
                )
            }

            composable(
                route = Routes.CATEGORY_EDIT_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Routes.ARG_SCOPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val container = LocalAppContainer.current
                val id = entry.arguments?.getString(Routes.ARG_ID)
                val scope = entry.arguments?.getString(Routes.ARG_SCOPE)
                    ?.let { name -> CategoryScope.entries.firstOrNull { it.name == name } }
                    ?: CategoryScope.EXPENSE

                val viewModel: CategoryEditViewModel = viewModel(
                    factory = ViewModelFactories.categoryEdit(container, id, scope),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                CategoryEditScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBack = navController::popBackStack,
                )
            }

            // --- Finance ---------------------------------------------------
            composable(Routes.BUDGETS) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_budgets),
                    phaseNote = "Budgets and local alerts arrive in Phase 6.",
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.CALENDAR) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_calendar),
                    phaseNote = "The calendar view arrives in Phase 7.",
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.CURRENCIES) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_currencies),
                    phaseNote = "Currency management arrives in Phase 9.",
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.EXCHANGE_RATES) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_exchange_rates),
                    phaseNote = "Manual exchange rates arrive in Phase 9.",
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.PAYMENT_METHODS) {
                val container = LocalAppContainer.current
                val viewModel: PaymentMethodViewModel = viewModel(
                    factory = ViewModelFactories.paymentMethods(container),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                PaymentMethodScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBack = navController::popBackStack,
                )
            }

            // --- Data ------------------------------------------------------
            composable(Routes.BACKUP) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_backup),
                    phaseNote = "JSON backup/restore and CSV export/import arrive in Phase 10.",
                    onBack = navController::popBackStack,
                )
            }

            // --- Settings --------------------------------------------------
            composable(Routes.SETTINGS) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_settings),
                    phaseNote = "Settings arrive alongside the features they configure.",
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.ABOUT) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_about),
                    phaseNote = stringResource(R.string.privacy_statement),
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}
