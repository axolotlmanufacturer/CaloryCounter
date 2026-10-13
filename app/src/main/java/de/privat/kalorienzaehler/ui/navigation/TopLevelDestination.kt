package de.privat.kalorienzaehler.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import de.privat.kalorienzaehler.R

/** The four tabs of the bottom navigation, in display order. */
enum class TopLevelDestination(
    val route: NavKey,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayRoute, R.string.nav_today, R.drawable.ic_today_24, R.drawable.ic_today_filled_24),
    WEIGHT(WeightRoute, R.string.nav_weight, R.drawable.ic_monitor_weight_24, R.drawable.ic_monitor_weight_filled_24),
    STATS(StatsRoute, R.string.nav_stats, R.drawable.ic_bar_chart_24, R.drawable.ic_bar_chart_filled_24),
    PROFILE(ProfileRoute, R.string.nav_profile, R.drawable.ic_person_24, R.drawable.ic_person_filled_24),
}
