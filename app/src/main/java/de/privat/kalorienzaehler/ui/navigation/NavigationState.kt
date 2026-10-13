package de.privat.kalorienzaehler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Creates and remembers the navigation state: one back stack per top-level route (tab), plus the
 * currently selected tab. Both survive configuration changes and process death.
 * Pattern: android/nav3-recipes "multiplestacks".
 */
@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
): NavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute,
        topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer()),
    ) {
        mutableStateOf(startRoute)
    }

    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks,
        )
    }
}

/**
 * Holds one back stack per top-level route. State of tabs that are not shown is retained.
 * The start route is always at the bottom ("exit through home").
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {

    /** The selected tab. */
    var topLevelRoute: NavKey by topLevelRoute

    /** The destination currently on top of the selected tab's back stack. */
    val currentKey: NavKey
        get() = checkNotNull(backStacks[topLevelRoute]?.lastOrNull()) {
            "Back stack for $topLevelRoute is empty"
        }

    /** True while a tab's own root screen is shown, i.e. the bottom navigation bar should be visible. */
    val isAtTopLevelRoot: Boolean
        get() = currentKey == topLevelRoute

    /** Entries of the back stacks that are in use, decorated with saved state and ViewModel stores. */
    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            )
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }
        return topLevelRoutesInUse().flatMap { decoratedEntries[it].orEmpty() }
    }

    /**
     * The start route's stack is always first; at most one other tab is shown on top of it, so
     * back from any tab returns to the start tab before the app is left.
     */
    private fun topLevelRoutesInUse(): List<NavKey> =
        if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)
}
