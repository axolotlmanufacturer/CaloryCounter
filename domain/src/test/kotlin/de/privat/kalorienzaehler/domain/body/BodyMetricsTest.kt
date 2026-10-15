package de.privat.kalorienzaehler.domain.body

import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.Sex
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyMetricsTest {

    @Test
    fun ageCountsCompletedYears() {
        val today = LocalDate.of(2026, 10, 13)
        assertEquals(30, BodyMetrics.ageYears(LocalDate.of(1996, 10, 13), today))
        assertEquals(29, BodyMetrics.ageYears(LocalDate.of(1996, 10, 14), today))
        assertEquals(30, BodyMetrics.ageYears(LocalDate.of(1996, 10, 12), today))
        assertEquals(17, BodyMetrics.ageYears(LocalDate.of(2008, 10, 14), today))
        assertEquals(18, BodyMetrics.ageYears(LocalDate.of(2008, 10, 13), today))
    }

    @Test
    fun leapDayBirthdayCountsFromMarch1stInCommonYears() {
        val birth = LocalDate.of(2000, 2, 29)
        assertEquals(25, BodyMetrics.ageYears(birth, LocalDate.of(2026, 2, 28)))
        assertEquals(26, BodyMetrics.ageYears(birth, LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun futureBirthDateGivesNegativeAge() {
        assertEquals(-1, BodyMetrics.ageYears(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun bmiIsWeightOverHeightSquared() {
        assertEquals(24.69, BodyMetrics.bmi(80.0, 180.0), 0.01)
        assertEquals(18.5, BodyMetrics.bmi(74.0, 200.0), 1e-9)
    }

    @Test
    fun minimumWeightForNormalBmiIsRoundedUpToATenthOfAKilo() {
        assertEquals(60.0, BodyMetrics.minWeightForNormalBmi(180.0), 1e-9) // 59,94 → 60,0
        assertEquals(74.0, BodyMetrics.minWeightForNormalBmi(200.0), 1e-9) // exactly 74,0, no rounding noise
        assertEquals(50.4, BodyMetrics.minWeightForNormalBmi(165.0), 1e-9) // 50,37 → 50,4
        listOf(150.0, 165.0, 172.5, 180.0, 193.0).forEach { height ->
            val min = BodyMetrics.minWeightForNormalBmi(height)
            assert(BodyMetrics.bmi(min, height) >= BodyMetrics.MIN_NORMAL_BMI) { "BMI of $min kg at $height cm" }
            assert(BodyMetrics.bmi(min - 0.1, height) < BodyMetrics.MIN_NORMAL_BMI) { "$min kg is not minimal at $height cm" }
        }
    }

    @Test
    fun mifflinStJeorMatchesTheSpecVectors() {
        assertEquals(1780.0, Bmr.mifflinStJeor(Sex.MALE, 80.0, 180.0, 30), 1e-9)
        assertEquals(1370.25, Bmr.mifflinStJeor(Sex.FEMALE, 65.0, 165.0, 30), 1e-9)
    }

    @Test
    fun katchMcArdleUsesLeanBodyMass() {
        // 80 kg at 20 % body fat → 64 kg lean mass → 370 + 21,6 × 64
        assertEquals(1752.4, Bmr.katchMcArdle(80.0, 20.0), 1e-9)
        assertEquals(370.0 + 21.6 * 80.0, Bmr.katchMcArdle(80.0, 0.0), 1e-9)
    }

    @Test
    fun tdeeMultipliesByTheActivityFactor() {
        assertEquals(2759.0, Tdee.of(1780.0, ActivityLevel.MODERATE), 1e-9)
        assertEquals(1884.09375, Tdee.of(1370.25, ActivityLevel.LIGHT), 1e-9)
        assertEquals(
            listOf(1.2, 1.375, 1.55, 1.725, 1.9),
            ActivityLevel.entries.map { it.factor },
        )
    }
}
