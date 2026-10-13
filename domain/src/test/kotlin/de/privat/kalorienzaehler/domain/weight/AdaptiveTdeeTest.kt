package de.privat.kalorienzaehler.domain.weight

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdaptiveTdeeTest {

    private val start = LocalDate.of(2026, 9, 1)
    private fun day(n: Int): LocalDate = start.plusDays(n.toLong())

    private fun intake(days: IntRange, kcal: Double = 2000.0): Map<LocalDate, Double> = days.associate { day(it) to kcal }
    private fun weights(days: List<Int>, kg: (Int) -> Double): List<WeightPoint> = days.map { WeightPoint(day(it), kg(it)) }

    @Test
    fun stableWeightMeansTdeeEqualsIntake() {
        val result = AdaptiveTdee.estimate(
            windowStart = day(0),
            windowEnd = day(27),
            dailyIntakeKcal = intake(0..27, 2300.0),
            weightEntries = weights((0..27 step 2).toList()) { 80.0 },
        )
        val estimate = assertIs<AdaptiveTdeeResult.Estimate>(result)
        assertEquals(2300.0, estimate.tdeeKcal, 1e-6)
        assertEquals(28, estimate.loggedDays)
        assertEquals(14, estimate.weightEntries)
        assertEquals(27, estimate.spanDays)
    }

    @Test
    fun aFallingTrendMeansTheBodyBurnsMoreThanItEats() {
        val entries = weights((0..27).toList()) { 80.0 - 0.1 * it }
        val result = assertIs<AdaptiveTdeeResult.Estimate>(
            AdaptiveTdee.estimate(day(0), day(27), intake(0..27), entries),
        )
        val series = WeightTrend.series(entries)
        val expectedChange = series.last().kg - series.first().kg
        assertEquals(expectedChange, result.trendChangeKg, 1e-9)
        assertEquals(2000.0 - expectedChange * 7700.0 / 27.0, result.tdeeKcal, 1e-6)
        assertTrue(result.tdeeKcal > 2000.0)
    }

    @Test
    fun aRisingTrendMeansTheBodyBurnsLessThanItEats() {
        val entries = weights((0..27).toList()) { 80.0 + 0.05 * it }
        val result = assertIs<AdaptiveTdeeResult.Estimate>(
            AdaptiveTdee.estimate(day(0), day(27), intake(0..27), entries),
        )
        assertTrue(result.tdeeKcal < 2000.0)
    }

    @Test
    fun onlyLoggedDaysEnterTheAverage() {
        val logged = intake(0..9, 2000.0) + intake(10..13, 0.0) + mapOf(day(20) to 3000.0)
        val result = assertIs<AdaptiveTdeeResult.Estimate>(
            AdaptiveTdee.estimate(day(0), day(27), logged, weights((0..27 step 3).toList()) { 80.0 }),
        )
        assertEquals(11, result.loggedDays) // zero-kcal days do not count as logged
        assertEquals((10 * 2000.0 + 3000.0) / 11.0, result.tdeeKcal, 1e-6)
    }

    @Test
    fun entriesOutsideTheWindowAreIgnoredButWarmUpTheTrend() {
        val entries = weights(listOf(-30, -20, -10), { 90.0 }) + weights((0..27 step 3).toList()) { 80.0 }
        val result = assertIs<AdaptiveTdeeResult.Estimate>(
            AdaptiveTdee.estimate(day(0), day(27), intake(0..27), entries),
        )
        assertEquals(10, result.weightEntries, "only entries inside the window count")
        // the trend is still falling towards 80 kg, so the body burns more than 2000 kcal
        assertTrue(result.tdeeKcal > 2000.0)
    }

    @Test
    fun whenTheTrendStartsInsideTheWindowTheSpanStartsAtTheFirstEntry() {
        val result = assertIs<AdaptiveTdeeResult.Estimate>(
            AdaptiveTdee.estimate(day(0), day(27), intake(0..27), weights((8..27 step 2).toList()) { 80.0 }),
        )
        assertEquals(19, result.spanDays) // day 8 → day 27
    }

    @Test
    fun aWindowShorterThanFourteenDaysIsNotEnough() {
        val result = assertIs<AdaptiveTdeeResult.NotEnoughData>(
            AdaptiveTdee.estimate(day(0), day(12), intake(0..12), weights((0..12).toList()) { 80.0 }),
        )
        assertEquals(setOf(Requirement.WINDOW_DAYS), result.missing)
    }

    @Test
    fun fourteenDaysAreEnough() {
        assertIs<AdaptiveTdeeResult.Estimate>(
            AdaptiveTdee.estimate(day(0), day(13), intake(0..13), weights((0..13).toList()) { 80.0 }),
        )
    }

    @Test
    fun tenLoggedDaysAndSixWeightEntriesAreTheMinimum() {
        val entries = weights((0..27 step 5).toList()) { 80.0 } // days 0,5,10,15,20,25 = 6 entries
        assertIs<AdaptiveTdeeResult.Estimate>(AdaptiveTdee.estimate(day(0), day(27), intake(0..9), entries))

        val tooFewLogged = AdaptiveTdee.estimate(day(0), day(27), intake(0..8), entries)
        assertEquals(setOf(Requirement.LOGGED_DAYS), assertIs<AdaptiveTdeeResult.NotEnoughData>(tooFewLogged).missing)

        val tooFewEntries = AdaptiveTdee.estimate(day(0), day(27), intake(0..27), entries.dropLast(1))
        assertEquals(setOf(Requirement.WEIGHT_ENTRIES), assertIs<AdaptiveTdeeResult.NotEnoughData>(tooFewEntries).missing)
    }

    @Test
    fun everythingMissingIsReportedTogether() {
        val result = assertIs<AdaptiveTdeeResult.NotEnoughData>(
            AdaptiveTdee.estimate(day(0), day(5), intake(0..2), weights(listOf(0, 3)) { 80.0 }),
        )
        assertEquals(setOf(Requirement.WINDOW_DAYS, Requirement.LOGGED_DAYS, Requirement.WEIGHT_ENTRIES), result.missing)
    }
}
