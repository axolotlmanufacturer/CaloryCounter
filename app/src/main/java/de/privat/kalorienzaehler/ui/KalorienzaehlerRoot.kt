package de.privat.kalorienzaehler.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.privat.kalorienzaehler.ui.navigation.ProfileRoute
import de.privat.kalorienzaehler.ui.navigation.StatsRoute
import de.privat.kalorienzaehler.ui.navigation.TodayRoute
import de.privat.kalorienzaehler.ui.navigation.TopLevelDestination
import de.privat.kalorienzaehler.ui.navigation.WeightRoute
import de.privat.kalorienzaehler.ui.settings.ProfileScreen
import de.privat.kalorienzaehler.ui.stats.StatsScreen
import de.privat.kalorienzaehler.ui.today.TodayScreen
import de.privat.kalorienzaehler.ui.weight.WeightScreen

/** App root: bottom navigation (Heute · Gewicht · Statistik · Profil) around the nav host. */
@Composable
fun KalorienzaehlerRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            // Full-screen routes added in later phases hide the bottom bar.
            if (currentDestination == null || currentDestination.isTopLevel()) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.isInHierarchy(destination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (selected) destination.selectedIcon else destination.icon,
                                    ),
                                    // The visible label already names the tab for TalkBack.
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<WeightRoute> { WeightScreen() }
            composable<StatsRoute> { StatsScreen() }
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}

private fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

private fun NavDestination.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { isInHierarchy(it) }

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Keep one back stack entry per tab and restore each tab's state when re-selected.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
