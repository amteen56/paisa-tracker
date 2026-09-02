package com.amteen.paisa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.amteen.paisa.R

enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        route = Routes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    TRANSACTIONS(
        route = Routes.TRANSACTIONS,
        labelRes = R.string.nav_expenses,
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
    ),
    REPORTS(
        route = Routes.REPORTS,
        labelRes = R.string.nav_reports,
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart,
    ),
    MORE(
        route = Routes.MORE,
        labelRes = R.string.nav_more,
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz,
    ),
}

@Composable
fun PaisaBottomBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) navController.navigateToTopLevel(destination)
                },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        // The label below is always visible, so the icon would only
                        // repeat it for a screen reader.
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Standard bottom-nav behaviour: single top-level entry on the back stack, state
 * preserved per tab, and Back from any tab returns to Home rather than walking
 * the tab history.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** True when the bottom bar and FAB should be shown for [route]. */
fun isTopLevelRoute(route: String?): Boolean =
    TopLevelDestination.entries.any { it.route == route }
