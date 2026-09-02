package com.amteen.paisa.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amteen.paisa.R
import com.amteen.paisa.ui.components.PlaceholderScreen
import com.amteen.paisa.ui.screen.more.MoreScreen

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
            modifier = Modifier.fillMaxSize(),
        ) {
            // --- Bottom navigation ---------------------------------------
            composable(Routes.HOME) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_home),
                    phaseNote = "The dashboard — balance, today's spending, budgets, " +
                        "top categories and recent transactions — arrives in Phase 5.",
                )
            }
            composable(Routes.TRANSACTIONS) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_expenses),
                    phaseNote = "Transaction history with search, filters and sorting " +
                        "arrives in Phase 3.",
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
                val type = entry.arguments?.getString(Routes.ARG_TYPE)
                val isIncome = type == TransactionTypeArg.INCOME
                PlaceholderScreen(
                    title = stringResource(
                        if (isIncome) R.string.title_add_income else R.string.title_add_expense,
                    ),
                    phaseNote = "Fast transaction entry arrives in Phase 3.",
                    onBack = navController::popBackStack,
                )
            }
            composable(
                route = Routes.TRANSACTION_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.StringType },
                ),
            ) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_transaction_details),
                    phaseNote = "Transaction details arrive in Phase 3.",
                    onBack = navController::popBackStack,
                )
            }
            composable(
                route = Routes.EDIT_TRANSACTION_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.StringType },
                ),
            ) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_edit_transaction),
                    phaseNote = "Editing arrives in Phase 3.",
                    onBack = navController::popBackStack,
                )
            }

            // --- Categories ------------------------------------------------
            composable(Routes.CATEGORIES) {
                PlaceholderScreen(
                    title = stringResource(R.string.title_categories),
                    phaseNote = "Category and subcategory management arrives in Phase 4.",
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
                PlaceholderScreen(
                    title = stringResource(R.string.title_payment_methods),
                    phaseNote = "Payment method management arrives in Phase 4.",
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
