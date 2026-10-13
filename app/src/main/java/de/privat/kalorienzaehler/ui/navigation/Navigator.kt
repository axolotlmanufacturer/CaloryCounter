package de.privat.kalorienzaehler.ui.navigation

import androidx.navigation3.runtime.NavKey

/** Mutates a [NavigationState]: switching tabs, pushing destinations and going back. */
class Navigator(val state: NavigationState) {

    /** Switches to [route] if it is a tab, otherwise pushes it onto the selected tab's back stack. */
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            checkNotNull(state.backStacks[state.topLevelRoute]).add(route)
        }
    }

    /**
     * Pops the selected tab's back stack. At the root of a tab other than the start tab this
     * returns to the start tab. Returns false at the root of the start tab (leave the app).
     */
    fun goBack(): Boolean {
        val stack = checkNotNull(state.backStacks[state.topLevelRoute]) {
            "Stack for ${state.topLevelRoute} not found"
        }
        return when {
            stack.last() != state.topLevelRoute -> {
                stack.removeAt(stack.lastIndex)
                true
            }
            state.topLevelRoute != state.startRoute -> {
                state.topLevelRoute = state.startRoute
                true
            }
            else -> false
        }
    }
}
