package de.privat.kalorienzaehler.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.privat.kalorienzaehler.R
import kotlin.reflect.KClass

/** The four tabs of the bottom navigation, in display order. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayRoute, TodayRoute::class, R.string.nav_today, R.drawable.ic_today_24, R.drawable.ic_today_filled_24),
    WEIGHT(WeightRoute, WeightRoute::class, R.string.nav_weight, R.drawable.ic_monitor_weight_24, R.drawable.ic_monitor_weight_filled_24),
    STATS(StatsRoute, StatsRoute::class, R.string.nav_stats, R.drawable.ic_bar_chart_24, R.drawable.ic_bar_chart_filled_24),
    PROFILE(ProfileRoute, ProfileRoute::class, R.string.nav_profile, R.drawable.ic_person_24, R.drawable.ic_person_filled_24),
}
