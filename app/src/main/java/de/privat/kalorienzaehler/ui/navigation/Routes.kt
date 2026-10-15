package de.privat.kalorienzaehler.ui.navigation

import androidx.navigation3.runtime.NavKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import kotlinx.serialization.Serializable

// Navigation 3 keys. They are @Serializable so the back stacks survive process death.
// Days travel as epoch days (a plain Long) because LocalDate is not serializable out of the box.

@Serializable
data object TodayRoute : NavKey

@Serializable
data object WeightRoute : NavKey

@Serializable
data object StatsRoute : NavKey

@Serializable
data object ProfileRoute : NavKey

/** Full screen: pick a food for [meal] on the day [epochDay]. */
@Serializable
data class AddFoodRoute(val epochDay: Long, val meal: Meal) : NavKey

/** Full screen: Schnelleintrag. With [entryId] it edits that quick entry instead of adding one. */
@Serializable
data class QuickEntryRoute(val epochDay: Long, val meal: Meal, val entryId: Long? = null) : NavKey

/** Full screen: amount, meal and preview, either for a new entry or for an existing one. */
@Serializable
data class PortionRoute(val target: PortionTarget) : NavKey

@Serializable
sealed interface PortionTarget {
    /** Log the food [ref] of [source] (BLS code, barcode or custom food id) to [meal] on the day [epochDay]. */
    @Serializable
    data class NewFromFood(val source: FoodSource, val ref: String, val epochDay: Long, val meal: Meal) : PortionTarget

    /** Change the amount or meal of the logged entry [entryId]. */
    @Serializable
    data class ExistingEntry(val entryId: Long) : PortionTarget
}

/** Full screen: scan (or type) a barcode to find a food for [meal] on the day [epochDay]. */
@Serializable
data class ScannerRoute(val epochDay: Long, val meal: Meal) : NavKey

/**
 * Full screen: create ([customFoodId] = null) or edit a custom food. When created from the add flow,
 * [logTo] says where to continue: straight to the amount screen for the new food. A [barcode] (from a scan that
 * found nothing) is filled in, so the next scan finds the new food.
 */
@Serializable
data class CustomFoodRoute(val customFoodId: Long? = null, val logTo: LogTarget? = null, val barcode: String? = null) : NavKey

/** A day and meal that a food is being logged to. */
@Serializable
data class LogTarget(val epochDay: Long, val meal: Meal)
