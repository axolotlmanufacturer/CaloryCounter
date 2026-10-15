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

    /** Replaces the destination on top of the selected tab's back stack (e.g. a form by its result). */
    fun replaceCurrent(route: NavKey) {
        val stack = checkNotNull(state.backStacks[state.topLevelRoute]) { "Stack for ${state.topLevelRoute} not found" }
        if (stack.last() != state.topLevelRoute) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    /** Leaves a multi-step flow: pops everything above the selected tab's own root screen. */
    fun popToTopLevelRoot() {
        val stack = checkNotNull(state.backStacks[state.topLevelRoute]) { "Stack for ${state.topLevelRoute} not found" }
        while (stack.last() != state.topLevelRoute) stack.removeAt(stack.lastIndex)
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
