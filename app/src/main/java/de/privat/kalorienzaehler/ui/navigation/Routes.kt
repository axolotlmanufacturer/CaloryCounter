package de.privat.kalorienzaehler.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. Full-screen routes (search, scanner, food detail)
// are added in the phases that implement them.

@Serializable
data object TodayRoute

@Serializable
data object WeightRoute

@Serializable
data object StatsRoute

@Serializable
data object ProfileRoute
