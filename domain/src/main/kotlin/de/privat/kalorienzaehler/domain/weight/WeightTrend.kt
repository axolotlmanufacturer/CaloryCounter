package de.privat.kalorienzaehler.domain.weight

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WeightPoint(val date: LocalDate, val kg: Double)

/**
 * Trend weight: exponential moving average with α = 0,1 over the daily entries (PROMPT.md §6.6).
 * Days without an entry carry the trend forward; [interpolated] fills them linearly for charts only.
 */
object WeightTrend {

    const val ALPHA: Double = 0.1

    /** Trend value at each entry date, ascending. Several entries on one day: the last one counts. */
    fun series(entries: List<WeightPoint>, alpha: Double = ALPHA): List<WeightPoint> {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
        val daily = entries.groupBy { it.date }.map { (date, sameDay) -> WeightPoint(date, sameDay.last().kg) }
            .sortedBy { it.date }
        val result = ArrayList<WeightPoint>(daily.size)
        var trend = Double.NaN
        for (point in daily) {
            trend = if (trend.isNaN()) point.kg else trend + alpha * (point.kg - trend)
            result += WeightPoint(point.date, trend)
        }
        return result
    }

    /** Trend on [date]: the latest trend point on or before it (carried forward), or null before the first entry. */
    fun trendOn(series: List<WeightPoint>, date: LocalDate): Double? =
        series.lastOrNull { !it.date.isAfter(date) }?.kg

    /** Trend change over the last [days] days up to [end]; null if there was no trend at the start. */
    fun change(series: List<WeightPoint>, end: LocalDate, days: Int): Double? {
        val now = trendOn(series, end) ?: return null
        val before = trendOn(series, end.minusDays(days.toLong())) ?: return null
        return now - before
    }

    /**
     * One point per day from the first trend point (or [from], whichever is later) to [to]:
     * linear between known trend points, flat after the last one. For charts only.
     */
    fun interpolated(series: List<WeightPoint>, from: LocalDate, to: LocalDate): List<WeightPoint> {
        if (series.isEmpty() || to.isBefore(series.first().date)) return emptyList()
        val start = maxOf(from, series.first().date)
        val result = ArrayList<WeightPoint>()
        var day = start
        while (!day.isAfter(to)) {
            result += WeightPoint(day, valueAt(series, day))
            day = day.plusDays(1)
        }
        return result
    }

    private fun valueAt(series: List<WeightPoint>, date: LocalDate): Double {
        val nextIndex = series.indexOfFirst { it.date.isAfter(date) }
        if (nextIndex == -1) return series.last().kg
        val next = series[nextIndex]
        if (nextIndex == 0) return next.kg
        val previous = series[nextIndex - 1]
        val span = ChronoUnit.DAYS.between(previous.date, next.date).toDouble()
        val elapsed = ChronoUnit.DAYS.between(previous.date, date).toDouble()
        return previous.kg + (next.kg - previous.kg) * elapsed / span
    }
}
