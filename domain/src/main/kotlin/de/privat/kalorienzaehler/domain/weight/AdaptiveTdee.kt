package de.privat.kalorienzaehler.domain.weight

import de.privat.kalorienzaehler.domain.goal.GoalPlanner
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Result of [AdaptiveTdee.estimate]. */
sealed interface AdaptiveTdeeResult {

    /** The estimated actual expenditure; offered to the user as a suggestion, never applied automatically. */
    data class Estimate(
        val tdeeKcal: Double,
        val loggedDays: Int,
        val weightEntries: Int,
        val trendChangeKg: Double,
        val spanDays: Int,
    ) : AdaptiveTdeeResult

    data class NotEnoughData(val missing: Set<Requirement>) : AdaptiveTdeeResult
}

enum class Requirement { WINDOW_DAYS, LOGGED_DAYS, WEIGHT_ENTRIES }

/**
 * Adaptive TDEE (PROMPT.md §6.7): `average daily intake − Δ trend kg × 7700 ÷ days`, once the window
 * covers at least 14 days with at least 10 logged days and 6 weight entries.
 */
object AdaptiveTdee {

    const val MIN_WINDOW_DAYS: Int = 14
    const val MIN_LOGGED_DAYS: Int = 10
    const val MIN_WEIGHT_ENTRIES: Int = 6

    /**
     * @param dailyIntakeKcal total kcal per day, only for days on which something was logged
     * @param weightEntries all weight entries (older ones warm up the trend)
     */
    fun estimate(
        windowStart: LocalDate,
        windowEnd: LocalDate,
        dailyIntakeKcal: Map<LocalDate, Double>,
        weightEntries: List<WeightPoint>,
    ): AdaptiveTdeeResult {
        val missing = linkedSetOf<Requirement>()

        val loggedDays = dailyIntakeKcal.filter { (date, kcal) -> kcal > 0.0 && date in windowStart..windowEnd }
        val entriesInWindow = weightEntries.filter { it.date in windowStart..windowEnd }
        val series = WeightTrend.series(weightEntries.filter { !it.date.isAfter(windowEnd) })

        // The trend may only start inside the window; the span then starts at the first entry.
        val effectiveStart = if (WeightTrend.trendOn(series, windowStart) != null) {
            windowStart
        } else {
            entriesInWindow.minOfOrNull { it.date } ?: windowEnd
        }
        val windowDays = ChronoUnit.DAYS.between(effectiveStart, windowEnd).toInt() + 1

        if (windowDays < MIN_WINDOW_DAYS) missing += Requirement.WINDOW_DAYS
        if (loggedDays.size < MIN_LOGGED_DAYS) missing += Requirement.LOGGED_DAYS
        if (entriesInWindow.size < MIN_WEIGHT_ENTRIES) missing += Requirement.WEIGHT_ENTRIES
        if (missing.isNotEmpty()) return AdaptiveTdeeResult.NotEnoughData(missing)

        val trendStart = checkNotNull(WeightTrend.trendOn(series, effectiveStart))
        val trendEnd = checkNotNull(WeightTrend.trendOn(series, windowEnd))
        val trendChangeKg = trendEnd - trendStart
        val spanDays = windowDays - 1
        val averageIntake = loggedDays.values.average()
        val tdee = averageIntake - trendChangeKg * GoalPlanner.KCAL_PER_KG / spanDays

        return AdaptiveTdeeResult.Estimate(
            tdeeKcal = tdee,
            loggedDays = loggedDays.size,
            weightEntries = entriesInWindow.size,
            trendChangeKg = trendChangeKg,
            spanDays = spanDays,
        )
    }
}
