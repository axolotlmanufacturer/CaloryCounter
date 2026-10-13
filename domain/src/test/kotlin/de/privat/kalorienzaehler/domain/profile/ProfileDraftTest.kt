package de.privat.kalorienzaehler.domain.profile

import de.privat.kalorienzaehler.domain.TODAY
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.goal.BirthDateIssue
import de.privat.kalorienzaehler.domain.goal.GoalNotice
import de.privat.kalorienzaehler.domain.goal.GoalProgress
import de.privat.kalorienzaehler.domain.goal.GoalWeightIssue
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileDraftTest {

    /** The first §6.8 vector as a complete draft (30 years old on [TODAY]). */
    private fun draft() = ProfileDraft(
        sex = Sex.MALE,
        birthDate = "13.09.1996",
        heightCm = "180",
        weightKg = "80",
        activity = ActivityLevel.MODERATE,
    )

    @Test
    fun aCompleteDraftBecomesAProfileAndAPlan() {
        val result = draft().evaluate(TODAY)
        assertTrue(result.isValid)
        val profile = assertNotNull(result.profile)
        assertEquals(Sex.MALE, profile.sex)
        assertEquals(180.0, profile.heightCm)
        assertEquals(ActivityLevel.MODERATE, profile.activityLevel)
        assertEquals(Goal.MAINTAIN, profile.goal)
        assertEquals(80.0, result.weightKg)
        assertEquals(30, result.ageYears)
        assertEquals(2759.0, assertNotNull(result.plan).tdeeKcal, 1e-9)
    }

    @Test
    fun theFirstSpecVectorWorksEndToEndFromTypedInput() {
        val result = draft().withGoalType(GoalType.LOSE).copy(weeklyRateKg = 0.5, goalWeightKg = "70").evaluate(TODAY)
        assertTrue(result.isValid, result.errors.toString())
        val plan = assertNotNull(result.plan)
        assertEquals(2209.0, plan.targetKcal, 1e-9)
        assertIs<GoalProgress.Projected>(plan.progress)
    }

    @Test
    fun germanAndEnglishDecimalSeparatorsBothWork() {
        val comma = draft().copy(heightCm = "180,5", weightKg = "72,4").evaluate(TODAY)
        val dot = draft().copy(heightCm = "180.5", weightKg = "72.4").evaluate(TODAY)
        assertEquals(72.4, comma.weightKg)
        assertEquals(comma.weightKg, dot.weightKg)
        assertEquals(180.5, comma.profile?.heightCm)
        assertEquals(comma.profile, dot.profile)
    }

    @Test
    fun emptyDraftReportsEveryRequiredField() {
        val result = ProfileDraft().evaluate(TODAY)
        assertEquals(
            setOf(DraftField.SEX, DraftField.BIRTH_DATE, DraftField.HEIGHT, DraftField.WEIGHT, DraftField.ACTIVITY),
            result.errors.keys,
        )
        assertTrue(result.errors.values.all { it == DraftError.Required })
        assertNull(result.profile)
        assertNull(result.plan)
    }

    @Test
    fun unreadableAndImplausibleInputIsToldApart() {
        val result = draft().copy(birthDate = "gestern", heightCm = "1,80", weightKg = "viel").evaluate(TODAY)
        assertEquals(DraftError.Unreadable, result.errors[DraftField.BIRTH_DATE])
        assertEquals(DraftError.OutOfRange, result.errors[DraftField.HEIGHT]) // 1,80 is a typo for 180
        assertEquals(DraftError.Unreadable, result.errors[DraftField.WEIGHT])
    }

    @Test
    fun birthDateIssues() {
        assertEquals(
            DraftError.BirthDate(BirthDateIssue.IN_FUTURE),
            draft().copy(birthDate = "01.01.2030").evaluate(TODAY).errors[DraftField.BIRTH_DATE],
        )
        assertEquals(
            DraftError.BirthDate(BirthDateIssue.TOO_YOUNG),
            draft().copy(birthDate = "01.01.2020").evaluate(TODAY).errors[DraftField.BIRTH_DATE],
        )
    }

    @Test
    fun bodyFatIsOptionalButCheckedWhenGiven() {
        assertTrue(draft().copy(bodyFatPercent = "").evaluate(TODAY).isValid)
        assertTrue(draft().copy(bodyFatPercent = "18,5").evaluate(TODAY).isValid)
        assertEquals(DraftError.OutOfRange, draft().copy(bodyFatPercent = "75").evaluate(TODAY).errors[DraftField.BODY_FAT])
        assertEquals(DraftError.Unreadable, draft().copy(bodyFatPercent = "x").evaluate(TODAY).errors[DraftField.BODY_FAT])
    }

    @Test
    fun katchMcArdleNeedsBodyFat() {
        val without = draft().copy(formula = BmrFormula.KATCH_MCARDLE).evaluate(TODAY)
        assertEquals(DraftError.BodyFatRequiredForKatchMcArdle, without.errors[DraftField.FORMULA])
        val with = draft().copy(formula = BmrFormula.KATCH_MCARDLE, bodyFatPercent = "20").evaluate(TODAY)
        assertTrue(with.isValid)
        assertEquals(1752.4, assertNotNull(with.plan).bmrKcal, 1e-9)
    }

    @Test
    fun loseAndGainNeedAValidGoalWeight() {
        val lose = draft().withGoalType(GoalType.LOSE)
        assertEquals(DraftError.GoalWeight(GoalWeightIssue.Missing), lose.evaluate(TODAY).errors[DraftField.GOAL_WEIGHT])
        assertEquals(DraftError.Unreadable, lose.copy(goalWeightKg = "abc").evaluate(TODAY).errors[DraftField.GOAL_WEIGHT])
        assertEquals(
            DraftError.GoalWeight(GoalWeightIssue.BelowNormalBmi(60.0)),
            lose.copy(goalWeightKg = "55").evaluate(TODAY).errors[DraftField.GOAL_WEIGHT],
        )
        assertEquals(
            DraftError.GoalWeight(GoalWeightIssue.NotBelowCurrent),
            lose.copy(goalWeightKg = "85").evaluate(TODAY).errors[DraftField.GOAL_WEIGHT],
        )
        val gain = draft().withGoalType(GoalType.GAIN)
        assertEquals(
            DraftError.GoalWeight(GoalWeightIssue.NotAboveCurrent),
            gain.copy(goalWeightKg = "75").evaluate(TODAY).errors[DraftField.GOAL_WEIGHT],
        )
        assertTrue(gain.copy(goalWeightKg = "85").evaluate(TODAY).isValid)
    }

    @Test
    fun theLivePreviewSurvivesAnIncompleteGoal() {
        val result = draft().withGoalType(GoalType.LOSE).evaluate(TODAY)
        assertNull(result.profile, "not saveable yet")
        val plan = assertNotNull(result.plan, "but the numbers are shown")
        assertEquals(2209.0, plan.targetKcal, 1e-9)
        assertEquals(GoalProgress.NoProjection, plan.progress)
    }

    @Test
    fun noGoalWeightIsRequiredWhereTheGoalDoesNotApply() {
        val minor = draft().copy(birthDate = "13.09.2010").withGoalType(GoalType.LOSE).evaluate(TODAY)
        assertTrue(minor.isValid, minor.errors.toString())
        assertTrue(GoalNotice.GoalRequiresAdult in assertNotNull(minor.plan).notices)

        val underweight = draft().copy(weightKg = "58").withGoalType(GoalType.LOSE).evaluate(TODAY)
        assertTrue(underweight.isValid, underweight.errors.toString())
        assertTrue(assertNotNull(underweight.plan).notices.any { it is GoalNotice.LoseNotOfferedBelowNormalBmi })
    }

    @Test
    fun maintenanceIgnoresAnyGoalWeight() {
        val result = draft().copy(goalWeightKg = "blah").evaluate(TODAY)
        assertTrue(result.isValid)
        assertEquals(0.0, result.profile?.goal?.weeklyRateKg)
    }

    @Test
    fun theEvaluationSaysWhichGoalsAreOffered() {
        val adult = draft().evaluate(TODAY)
        assertTrue(adult.goalAllowed)
        assertTrue(adult.loseOffered)

        val minor = draft().copy(birthDate = "13.09.2010").evaluate(TODAY)
        assertTrue(!minor.goalAllowed)
        assertTrue(!minor.loseOffered)

        val underweight = draft().copy(weightKg = "58").evaluate(TODAY) // BMI 17,9
        assertTrue(underweight.goalAllowed)
        assertTrue(!underweight.loseOffered)

        val unknown = ProfileDraft().evaluate(TODAY)
        assertTrue(unknown.goalAllowed && unknown.loseOffered, "nothing is blocked before the data is in")
    }

    @Test
    fun switchingTheGoalTypeKeepsTheRateValid() {
        val lose = draft().copy(weeklyRateKg = 1.0).withGoalType(GoalType.LOSE)
        assertEquals(1.0, lose.weeklyRateKg)
        val gain = lose.withGoalType(GoalType.GAIN)
        assertEquals(ProfileDraft.DEFAULT_GAIN_RATE_KG, gain.weeklyRateKg, "1,0 kg/week is not offered for gaining")
        val back = gain.withGoalType(GoalType.LOSE)
        assertEquals(0.25, back.weeklyRateKg, "0,25 is a valid loss rate and stays")
    }

    @Test
    fun customPercentSplitMustAddUpTo100() {
        val ok = draft().copy(macroMode = MacroMode.PERCENT, proteinPercent = "25", carbsPercent = "45", fatPercent = "30")
        assertEquals(MacroSettings.PercentSplit(25.0, 45.0, 30.0), ok.evaluate(TODAY).profile?.macros)
        val off = ok.copy(fatPercent = "40").evaluate(TODAY)
        assertEquals(DraftError.PercentsMustSumTo100, off.errors[DraftField.MACROS])
        assertNotNull(off.plan, "preview falls back to the default macros")
        assertEquals(DraftError.MacroValuesUnreadable, ok.copy(carbsPercent = "x").evaluate(TODAY).errors[DraftField.MACROS])
    }

    @Test
    fun customGramsPerKilogram() {
        val result = draft().copy(macroMode = MacroMode.PER_KG, proteinGPerKg = "2", fatGPerKg = "0,9").evaluate(TODAY)
        assertEquals(MacroSettings.PerKilogram(2.0, 0.9), result.profile?.macros)
        assertEquals(
            DraftError.MacroValuesUnreadable,
            draft().copy(macroMode = MacroMode.PER_KG, proteinGPerKg = "99").evaluate(TODAY).errors[DraftField.MACROS],
        )
    }

    @Test
    fun aProfileSurvivesTheRoundTripThroughADraft() {
        val original = draft().withGoalType(GoalType.GAIN).copy(
            weeklyRateKg = 0.5,
            goalWeightKg = "85,5",
            bodyFatPercent = "18,5",
            formula = BmrFormula.KATCH_MCARDLE,
            macroMode = MacroMode.PERCENT,
            proteinPercent = "30", carbsPercent = "45", fatPercent = "25",
            energyUnit = EnergyUnit.KJ,
        ).evaluate(TODAY)
        val profile = assertNotNull(original.profile)

        val again = ProfileDraft.from(profile, currentWeightKg = 80.0).evaluate(TODAY)
        assertEquals(profile, again.profile)
        assertEquals(80.0, again.weightKg)
    }
}
