package de.privat.kalorienzaehler.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 keys. They are @Serializable so the back stacks survive process death.
// Full-screen routes (search, scanner, food detail) are added in the phases that implement them.

@Serializable
data object TodayRoute : NavKey

@Serializable
data object WeightRoute : NavKey

@Serializable
data object StatsRoute : NavKey

@Serializable
data object ProfileRoute : NavKey
