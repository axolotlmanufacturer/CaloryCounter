package de.privat.kalorienzaehler.domain.goal

import de.privat.kalorienzaehler.domain.TODAY
import de.privat.kalorienzaehler.domain.model.GoalType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileValidationTest {

    @Test
    fun birthDateIssues() {
        assertNull(ProfileValidation.birthDate(LocalDate.of(1990, 5, 1), TODAY))
        assertEquals(BirthDateIssue.IN_FUTURE, ProfileValidation.birthDate(TODAY.plusDays(1), TODAY))
        assertEquals(BirthDateIssue.TOO_YOUNG, ProfileValidation.birthDate(TODAY.minusYears(9), TODAY))
        assertNull(ProfileValidation.birthDate(TODAY.minusYears(10), TODAY))
        assertNull(ProfileValidation.birthDate(TODAY.minusYears(110), TODAY))
        assertEquals(BirthDateIssue.TOO_OLD, ProfileValidation.birthDate(TODAY.minusYears(111), TODAY))
    }

    @Test
    fun rangesCatchTypos() {
        assertTrue(ProfileValidation.isHeightValid(180.0))
        assertFalse(ProfileValidation.isHeightValid(18.0))
        assertFalse(ProfileValidation.isHeightValid(1800.0))
        assertTrue(ProfileValidation.isWeightValid(72.4))
        assertFalse(ProfileValidation.isWeightValid(7.2))
        assertTrue(ProfileValidation.isBodyFatValid(18.5))
        assertFalse(ProfileValidation.isBodyFatValid(2.0))
        assertFalse(ProfileValidation.isBodyFatValid(75.0))
    }

    @Test
    fun loseIsOfferedToAdultsWithBmiOfAtLeast185() {
        assertTrue(ProfileValidation.isLoseOffered(ageYears = 18, weightKg = 80.0, heightCm = 180.0))
        assertFalse(ProfileValidation.isLoseOffered(ageYears = 17, weightKg = 80.0, heightCm = 180.0))
        assertFalse(ProfileValidation.isLoseOffered(ageYears = 30, weightKg = 59.0, heightCm = 180.0)) // BMI 18,2
        assertTrue(ProfileValidation.isLoseOffered(ageYears = 30, weightKg = 60.0, heightCm = 180.0)) // BMI 18,5
        assertFalse(ProfileValidation.isGoalAllowed(17))
        assertTrue(ProfileValidation.isGoalAllowed(18))
    }

    @Test
    fun maintenanceNeedsNoGoalWeight() {
        assertNull(ProfileValidation.goalWeight(GoalType.MAINTAIN, null, 80.0, 180.0))
        assertNull(ProfileValidation.goalWeight(GoalType.MAINTAIN, 10.0, 80.0, 180.0))
    }

    @Test
    fun loseAndGainNeedAGoalWeight() {
        assertEquals(GoalWeightIssue.Missing, ProfileValidation.goalWeight(GoalType.LOSE, null, 80.0, 180.0))
        assertEquals(GoalWeightIssue.Missing, ProfileValidation.goalWeight(GoalType.GAIN, null, 80.0, 180.0))
    }

    @Test
    fun goalWeightMustCorrespondToBmiOfAtLeast185() {
        val issue = ProfileValidation.goalWeight(GoalType.LOSE, 55.0, 80.0, 180.0)
        assertEquals(60.0, assertIs<GoalWeightIssue.BelowNormalBmi>(issue).minWeightKg, 1e-9)
        assertNull(ProfileValidation.goalWeight(GoalType.LOSE, 60.0, 80.0, 180.0))
        assertIs<GoalWeightIssue.BelowNormalBmi>(ProfileValidation.goalWeight(GoalType.LOSE, 59.9, 80.0, 180.0))
        // also while gaining: an underweight goal is no goal
        assertIs<GoalWeightIssue.BelowNormalBmi>(ProfileValidation.goalWeight(GoalType.GAIN, 58.0, 55.0, 180.0))
    }

    @Test
    fun goalWeightMustPointInTheRightDirection() {
        assertEquals(GoalWeightIssue.NotBelowCurrent, ProfileValidation.goalWeight(GoalType.LOSE, 80.0, 80.0, 180.0))
        assertEquals(GoalWeightIssue.NotBelowCurrent, ProfileValidation.goalWeight(GoalType.LOSE, 85.0, 80.0, 180.0))
        assertEquals(GoalWeightIssue.NotAboveCurrent, ProfileValidation.goalWeight(GoalType.GAIN, 80.0, 80.0, 180.0))
        assertEquals(GoalWeightIssue.NotAboveCurrent, ProfileValidation.goalWeight(GoalType.GAIN, 70.0, 80.0, 180.0))
        assertNull(ProfileValidation.goalWeight(GoalType.LOSE, 70.0, 80.0, 180.0))
        assertNull(ProfileValidation.goalWeight(GoalType.GAIN, 85.0, 80.0, 180.0))
    }
}
