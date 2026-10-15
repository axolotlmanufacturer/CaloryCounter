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
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.ui.addfood.AddFoodScreen
import de.privat.kalorienzaehler.ui.addfood.CustomFoodScreen
import de.privat.kalorienzaehler.ui.addfood.PortionScreen
import de.privat.kalorienzaehler.ui.addfood.QuickEntryScreen
import de.privat.kalorienzaehler.ui.navigation.AddFoodRoute
import de.privat.kalorienzaehler.ui.navigation.CustomFoodRoute
import de.privat.kalorienzaehler.ui.navigation.LogTarget
import de.privat.kalorienzaehler.ui.navigation.PortionRoute
import de.privat.kalorienzaehler.ui.navigation.PortionTarget
import de.privat.kalorienzaehler.ui.navigation.ProfileRoute
import de.privat.kalorienzaehler.ui.navigation.QuickEntryRoute
import de.privat.kalorienzaehler.ui.navigation.ScannerRoute
import de.privat.kalorienzaehler.ui.navigation.StatsRoute
import de.privat.kalorienzaehler.ui.navigation.TodayRoute
import de.privat.kalorienzaehler.ui.navigation.TopLevelDestination
import de.privat.kalorienzaehler.ui.navigation.WeightRoute
import de.privat.kalorienzaehler.ui.navigation.Navigator
import de.privat.kalorienzaehler.ui.navigation.rememberNavigationState
import de.privat.kalorienzaehler.ui.scanner.ScannerScreen
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
        entry<TodayRoute> {
            TodayScreen(
                onAddFood = { date, meal -> navigator.navigate(AddFoodRoute(date.toEpochDay(), meal)) },
                onEditEntry = { entry ->
                    navigator.navigate(
                        if (entry.isQuick) {
                            QuickEntryRoute(entry.date.toEpochDay(), entry.meal, entry.id)
                        } else {
                            PortionRoute(PortionTarget.ExistingEntry(entry.id))
                        },
                    )
                },
            )
        }
        entry<AddFoodRoute> { route ->
            AddFoodScreen(
                route = route,
                onBack = { navigator.goBack() },
                onQuickEntry = { navigator.navigate(QuickEntryRoute(route.epochDay, route.meal)) },
                onCreateCustom = { navigator.navigate(CustomFoodRoute(logTo = LogTarget(route.epochDay, route.meal))) },
                onScan = { navigator.navigate(ScannerRoute(route.epochDay, route.meal)) },
                onLogFood = { food ->
                    navigator.navigate(
                        PortionRoute(PortionTarget.NewFromFood(food.key.source, food.key.ref, route.epochDay, route.meal)),
                    )
                },
                onEditFood = { food -> food.key.ref.toLongOrNull()?.let { navigator.navigate(CustomFoodRoute(customFoodId = it)) } },
            )
        }
        entry<ScannerRoute> { route ->
            ScannerScreen(
                route = route,
                onBack = { navigator.goBack() },
                // The scanner is replaced, so "back" from the amount or the form returns to the add screen.
                onFoodFound = { key ->
                    navigator.replaceCurrent(
                        PortionRoute(PortionTarget.NewFromFood(key.source, key.ref, route.epochDay, route.meal)),
                    )
                },
                onCreateCustom = { code ->
                    navigator.replaceCurrent(CustomFoodRoute(logTo = LogTarget(route.epochDay, route.meal), barcode = code))
                },
            )
        }
        entry<QuickEntryRoute> { route ->
            QuickEntryScreen(route = route, onBack = { navigator.goBack() }, onDone = { navigator.popToTopLevelRoot() })
        }
        entry<PortionRoute> { route ->
            PortionScreen(route = route, onBack = { navigator.goBack() }, onDone = { navigator.popToTopLevelRoot() })
        }
        entry<CustomFoodRoute> { route ->
            val logTo = route.logTo
            CustomFoodScreen(
                route = route,
                onBack = { navigator.goBack() },
                onSaved = { foodId, isNew ->
                    if (isNew && logTo != null) {
                        // A new food from the add flow goes straight on to choosing the amount.
                        navigator.replaceCurrent(
                            PortionRoute(
                                PortionTarget.NewFromFood(FoodSource.CUSTOM, foodId.toString(), logTo.epochDay, logTo.meal),
                            ),
                        )
                    } else {
                        navigator.goBack()
                    }
                },
                onDeleted = { navigator.goBack() },
            )
        }
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
