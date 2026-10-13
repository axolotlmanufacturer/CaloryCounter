package de.privat.kalorienzaehler.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import de.privat.kalorienzaehler.ui.navigation.ProfileRoute
import de.privat.kalorienzaehler.ui.navigation.StatsRoute
import de.privat.kalorienzaehler.ui.navigation.TodayRoute
import de.privat.kalorienzaehler.ui.navigation.TopLevelDestination
import de.privat.kalorienzaehler.ui.navigation.WeightRoute
import de.privat.kalorienzaehler.ui.navigation.Navigator
import de.privat.kalorienzaehler.ui.navigation.rememberNavigationState
import de.privat.kalorienzaehler.ui.settings.ProfileScreen
import de.privat.kalorienzaehler.ui.stats.StatsScreen
import de.privat.kalorienzaehler.ui.today.TodayScreen
import de.privat.kalorienzaehler.ui.weight.WeightScreen

private val TOP_LEVEL_ROUTES: Set<NavKey> = TopLevelDestination.entries.map { it.route }.toSet()

/** App root: bottom navigation (Heute · Gewicht · Statistik · Profil) around the Navigation 3 display. */
@Composable
fun KalorienzaehlerRoot() {
    val navigationState = rememberNavigationState(
        startRoute = TodayRoute,
        topLevelRoutes = TOP_LEVEL_ROUTES,
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }

    val entryProvider = entryProvider {
        entry<TodayRoute> { TodayScreen() }
        entry<WeightRoute> { WeightScreen() }
        entry<StatsRoute> { StatsScreen() }
        entry<ProfileRoute> { ProfileScreen() }
    }

    Scaffold(
        // Insets are handled by the bottom bar and by each screen's own top app bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Full-screen routes added in later phases hide the bottom bar.
            if (navigationState.isAtTopLevelRoot) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = destination.route == navigationState.topLevelRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigator.navigate(destination.route) },
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
        NavDisplay(
            entries = navigationState.toDecoratedEntries(entryProvider),
            onBack = { navigator.goBack() },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}
