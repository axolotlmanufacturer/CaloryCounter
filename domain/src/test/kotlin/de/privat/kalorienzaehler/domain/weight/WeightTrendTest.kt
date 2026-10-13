package de.privat.kalorienzaehler.domain.weight

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightTrendTest {

    private val d1 = LocalDate.of(2026, 10, 1)
    private fun day(n: Int): LocalDate = d1.plusDays(n.toLong())
    private fun point(n: Int, kg: Double) = WeightPoint(day(n), kg)

    @Test
    fun trendStartsAtTheFirstEntryAndMovesTenPercentTowardsEachNewOne() {
        val series = WeightTrend.series(listOf(point(0, 80.0), point(1, 79.0), point(2, 81.0)))
        assertEquals(listOf(80.0, 79.9, 80.01), series.map { it.kg }.map { Math.round(it * 1e6) / 1e6 })
        assertEquals(listOf(day(0), day(1), day(2)), series.map { it.date })
    }

    @Test
    fun entriesAreSortedAndOneEntryPerDayCounts() {
        val series = WeightTrend.series(listOf(point(2, 82.0), point(0, 80.0), point(2, 81.0)))
        assertEquals(2, series.size)
        assertEquals(80.0, series[0].kg, 1e-9)
        assertEquals(80.0 + 0.1 * (81.0 - 80.0), series[1].kg, 1e-9) // the later entry of day 2 counts
    }

    @Test
    fun emptyInputGivesAnEmptySeries() {
        assertTrue(WeightTrend.series(emptyList()).isEmpty())
    }

    @Test
    fun gapsCarryTheTrendForward() {
        val series = WeightTrend.series(listOf(point(0, 80.0), point(10, 78.0)))
        assertNull(WeightTrend.trendOn(series, day(-1)))
        assertEquals(80.0, WeightTrend.trendOn(series, day(0))!!, 1e-9)
        assertEquals(80.0, WeightTrend.trendOn(series, day(9))!!, 1e-9) // no entry, no change
        assertEquals(79.8, WeightTrend.trendOn(series, day(10))!!, 1e-9)
        assertEquals(79.8, WeightTrend.trendOn(series, day(30))!!, 1e-9)
    }

    @Test
    fun aConstantWeightIsItsOwnTrend() {
        val series = WeightTrend.series((0..20).map { point(it, 72.4) })
        assertTrue(series.all { kotlin.math.abs(it.kg - 72.4) < 1e-9 })
    }

    @Test
    fun trendLagsBehindARealChange() {
        val series = WeightTrend.series((0..9).map { point(it, 80.0 - it) }) // 1 kg lighter per day
        val last = series.last().kg
        assertTrue(last > 71.0, "trend ($last) must lag behind the raw weight (71.0)")
        assertTrue(last < 80.0)
        assertTrue(series.zipWithNext().all { (a, b) -> b.kg < a.kg }, "trend must fall monotonically")
    }

    @Test
    fun changeOverDaysComparesTheTrendThenAndNow() {
        val series = WeightTrend.series(listOf(point(0, 80.0), point(7, 78.0), point(14, 76.0)))
        // trend: 80.0, 79.8, 79.42
        assertEquals(79.42 - 79.8, WeightTrend.change(series, day(14), 7)!!, 1e-9)
        assertEquals(79.42 - 80.0, WeightTrend.change(series, day(14), 14)!!, 1e-9)
        assertNull(WeightTrend.change(series, day(14), 15), "no trend 15 days ago")
        assertNull(WeightTrend.change(emptyList(), day(14), 7))
    }

    @Test
    fun chartInterpolationIsLinearBetweenPointsAndFlatAfterTheLast() {
        val series = listOf(WeightPoint(day(0), 80.0), WeightPoint(day(4), 78.0))
        val chart = WeightTrend.interpolated(series, from = day(-3), to = day(6))
        assertEquals(day(0), chart.first().date, "starts at the first trend point")
        assertEquals(day(6), chart.last().date)
        assertEquals(listOf(80.0, 79.5, 79.0, 78.5, 78.0, 78.0, 78.0), chart.map { it.kg })
    }

    @Test
    fun chartInterpolationRespectsALaterStartAndEmptyRanges() {
        val series = listOf(WeightPoint(day(0), 80.0), WeightPoint(day(4), 78.0))
        assertEquals(listOf(79.0, 78.5), WeightTrend.interpolated(series, day(2), day(3)).map { it.kg })
        assertTrue(WeightTrend.interpolated(series, day(-5), day(-1)).isEmpty())
        assertTrue(WeightTrend.interpolated(emptyList(), day(0), day(3)).isEmpty())
    }
}
