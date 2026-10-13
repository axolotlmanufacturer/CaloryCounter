package de.privat.kalorienzaehler.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    @Serializable
    private data object Detail : NavKey

    private fun navigator(): Navigator {
        val tabs = TopLevelDestination.entries.map { it.route }
        val state = NavigationState(
            startRoute = TodayRoute,
            topLevelRoute = mutableStateOf(TodayRoute),
            backStacks = tabs.associateWith { NavBackStack(it) },
        )
        return Navigator(state)
    }

    @Test
    fun startsOnTodayAtTheTopLevelRoot() {
        val navigator = navigator()
        assertEquals(TodayRoute, navigator.state.topLevelRoute)
        assertEquals(TodayRoute, navigator.state.currentKey)
        assertTrue(navigator.state.isAtTopLevelRoot)
    }

    @Test
    fun navigatingToATabSwitchesTheSelectedTab() {
        val navigator = navigator()
        navigator.navigate(StatsRoute)
        assertEquals(StatsRoute, navigator.state.topLevelRoute)
        assertTrue(navigator.state.isAtTopLevelRoot)
    }

    @Test
    fun pushedDestinationsLiveOnTheSelectedTabAndHideTheBottomBar() {
        val navigator = navigator()
        navigator.navigate(WeightRoute)
        navigator.navigate(Detail)
        assertEquals(Detail, navigator.state.currentKey)
        assertFalse(navigator.state.isAtTopLevelRoot)
        // Other tabs keep their own, untouched stacks.
        assertEquals(listOf<NavKey>(TodayRoute), navigator.state.backStacks.getValue(TodayRoute).toList())
        assertEquals(listOf(WeightRoute, Detail), navigator.state.backStacks.getValue(WeightRoute).toList())
    }

    @Test
    fun backPopsThePushedDestinationFirst() {
        val navigator = navigator()
        navigator.navigate(ProfileRoute)
        navigator.navigate(Detail)

        assertTrue(navigator.goBack())
        assertEquals(ProfileRoute, navigator.state.currentKey)
        assertEquals(ProfileRoute, navigator.state.topLevelRoute)
    }

    @Test
    fun backFromAnotherTabRootReturnsToTodayAndThenLeavesTheApp() {
        val navigator = navigator()
        navigator.navigate(StatsRoute)

        assertTrue(navigator.goBack())
        assertEquals(TodayRoute, navigator.state.topLevelRoute)

        assertFalse("back at the root of the start tab leaves the app", navigator.goBack())
        assertEquals(TodayRoute, navigator.state.topLevelRoute)
    }

    @Test
    fun aTabKeepsItsStackWhileAnotherTabIsSelected() {
        val navigator = navigator()
        navigator.navigate(WeightRoute)
        navigator.navigate(Detail)
        navigator.navigate(TodayRoute)
        navigator.navigate(WeightRoute)
        assertEquals(Detail, navigator.state.currentKey)
    }
}
