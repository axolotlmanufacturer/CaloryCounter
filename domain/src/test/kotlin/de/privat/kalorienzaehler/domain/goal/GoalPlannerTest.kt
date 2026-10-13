package de.privat.kalorienzaehler.domain.goal

import de.privat.kalorienzaehler.domain.TODAY
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import de.privat.kalorienzaehler.domain.profile
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GoalPlannerTest {

    private fun lose(rate: Double, goalWeight: Double? = 70.0) = Goal(GoalType.LOSE, rate, goalWeight)
    private fun gain(rate: Double, goalWeight: Double? = 90.0) = Goal(GoalType.GAIN, rate, goalWeight)

    // --- PROMPT.md §6.8 required test vectors ---------------------------------------------

    @Test
    fun maleVector_bmr1780_tdee2759_target2209() {
        val plan = GoalPlanner.plan(
            profile(Sex.MALE, age = 30, heightCm = 180.0, activity = ActivityLevel.MODERATE, goal = lose(0.5)),
            weightKg = 80.0,
            today = TODAY,
        )
        assertEquals(1780.0, plan.bmrKcal, 1e-9)
        assertEquals(2759.0, plan.tdeeKcal, 1e-9)
        assertEquals(2209.0, plan.targetKcal, 1e-9)
        assertEquals(-550.0, plan.dailyEnergyChangeKcal, 1e-9)
        assertEquals(0.5, plan.effectiveWeeklyRateKg, 1e-9)
        assertEquals(GoalType.LOSE, plan.goalType)
        assertTrue(plan.notices.isEmpty(), "no rule intervened: ${plan.notices}")
    }

    @Test
    fun femaleVector_rateIsCappedAtOnePercent_thenFloorClampsTo1200() {
        val plan = GoalPlanner.plan(
            profile(Sex.FEMALE, age = 30, heightCm = 165.0, activity = ActivityLevel.LIGHT, goal = lose(0.75)),
            weightKg = 65.0,
            today = TODAY,
        )
        assertEquals(1370.25, plan.bmrKcal, 1e-9)
        assertEquals(1884.09375, plan.tdeeKcal, 1e-9)
        assertEquals(1200.0, plan.targetKcal, 1e-9)
        assertEquals(0.62, plan.effectiveWeeklyRateKg, 0.005)

        val capped = plan.notices.filterIsInstance<GoalNotice.RateCappedToOnePercent>().single()
        assertEquals(0.75, capped.requestedKg, 1e-9)
        assertEquals(0.65, capped.cappedKg, 1e-9)
        val floor = plan.notices.filterIsInstance<GoalNotice.CalorieFloorApplied>().single()
        assertEquals(1200.0, floor.floorKcal, 1e-9)
        assertEquals(plan.effectiveWeeklyRateKg, floor.effectiveWeeklyRateKg, 1e-9)
    }

    @Test
    fun withoutTheFloorTheCappedRateWouldHaveGiven1169() {
        // 1884,09 − 0,65 × 1100 = 1169,09: below the floor, which is why the target is raised.
        assertEquals(1169.09375, 1884.09375 - GoalPlanner.dailyEnergyForWeeklyRate(0.65), 1e-9)
    }

    // --- Goals ------------------------------------------------------------------------------

    @Test
    fun maintenanceTargetIsTdee() {
        val plan = GoalPlanner.plan(profile(goal = Goal.MAINTAIN), weightKg = 80.0, today = TODAY)
        assertEquals(plan.tdeeKcal, plan.targetKcal, 1e-9)
        assertEquals(0.0, plan.dailyEnergyChangeKcal, 1e-9)
        assertEquals(0.0, plan.effectiveWeeklyRateKg, 1e-9)
        assertEquals(GoalProgress.NoProjection, plan.progress)
    }

    @Test
    fun gainAddsTheSurplusOfTheChosenRate() {
        val plan = GoalPlanner.plan(profile(goal = gain(0.5)), weightKg = 80.0, today = TODAY)
        assertEquals(2759.0 + 550.0, plan.targetKcal, 1e-9)
        assertEquals(550.0, plan.dailyEnergyChangeKcal, 1e-9)
        assertEquals(0.5, plan.effectiveWeeklyRateKg, 1e-9)
        assertEquals(GoalType.GAIN, plan.goalType)
    }

    @Test
    fun theFourLoseRatesAndTwoGainRatesMatchTheSpec() {
        assertEquals(listOf(0.25, 0.5, 0.75, 1.0), GoalPlanner.LOSE_RATES_KG)
        assertEquals(listOf(0.25, 0.5), GoalPlanner.GAIN_RATES_KG)
        assertEquals(275.0, GoalPlanner.dailyEnergyForWeeklyRate(0.25), 1e-9)
        assertEquals(1100.0, GoalPlanner.dailyEnergyForWeeklyRate(1.0), 1e-9)
    }

    @Test
    fun aHeavyPersonMayLoseOneKilogramPerWeek() {
        val plan = GoalPlanner.plan(
            profile(Sex.MALE, heightCm = 185.0, activity = ActivityLevel.HIGH, goal = lose(1.0, goalWeight = 100.0)),
            weightKg = 130.0,
            today = TODAY,
        )
        assertEquals(1.0, plan.effectiveWeeklyRateKg, 1e-9) // 1 % of 130 kg = 1,3 kg is not limiting
        assertTrue(plan.notices.none { it is GoalNotice.RateCappedToOnePercent })
    }

    // --- Safety rules (§6.4) --------------------------------------------------------------------

    @Test
    fun rule1_minorsGetNoWeightGoal() {
        val plan = GoalPlanner.plan(profile(age = 17, goal = lose(0.5)), weightKg = 70.0, today = TODAY)
        assertEquals(GoalType.MAINTAIN, plan.goalType)
        assertEquals(plan.tdeeKcal, plan.targetKcal, 1e-9)
        assertTrue(GoalNotice.GoalRequiresAdult in plan.notices)

        val gainPlan = GoalPlanner.plan(profile(age = 17, goal = gain(0.5)), weightKg = 70.0, today = TODAY)
        assertEquals(GoalType.MAINTAIN, gainPlan.goalType)
    }

    @Test
    fun rule1_eighteenIsOldEnough() {
        val plan = GoalPlanner.plan(profile(age = 18, goal = lose(0.5)), weightKg = 80.0, today = TODAY)
        assertEquals(GoalType.LOSE, plan.goalType)
        assertTrue(GoalNotice.GoalRequiresAdult !in plan.notices)
    }

    @Test
    fun rule1_birthdayTodayMakesAnAdult() {
        val onBirthday = profile(goal = lose(0.5)).copy(birthDate = TODAY.minusYears(18))
        assertEquals(GoalType.LOSE, GoalPlanner.plan(onBirthday, 80.0, TODAY).goalType)
        val dayBefore = profile(goal = lose(0.5)).copy(birthDate = TODAY.minusYears(18).plusDays(1))
        assertEquals(GoalType.MAINTAIN, GoalPlanner.plan(dayBefore, 80.0, TODAY).goalType)
    }

    @Test
    fun rule2_lossRateIsCappedAtOnePercentOfBodyWeight() {
        val plan = GoalPlanner.plan(
            profile(Sex.MALE, heightCm = 190.0, activity = ActivityLevel.HIGH, goal = lose(1.0, goalWeight = 60.0)),
            weightKg = 70.0,
            today = TODAY,
        )
        assertEquals(0.70, plan.effectiveWeeklyRateKg, 1e-9)
        val notice = plan.notices.filterIsInstance<GoalNotice.RateCappedToOnePercent>().single()
        assertEquals(1.0, notice.requestedKg, 1e-9)
        assertEquals(0.70, notice.cappedKg, 1e-9)
    }

    @Test
    fun rule3_maleFloorIs1500() {
        // 55 kg, 160 cm, 25 y, sedentary: TDEE 1716; 0,55 kg/week (1 % cap) would give 1111 kcal
        val plan = GoalPlanner.plan(
            profile(Sex.MALE, age = 25, heightCm = 160.0, activity = ActivityLevel.SEDENTARY, goal = lose(1.0, 50.0)),
            weightKg = 55.0,
            today = TODAY,
        )
        assertEquals(1716.0, plan.tdeeKcal, 1e-9)
        assertEquals(1500.0, plan.targetKcal, 1e-9)
        assertEquals((1716.0 - 1500.0) * 7.0 / 7700.0, plan.effectiveWeeklyRateKg, 1e-9)
        assertTrue(plan.notices.any { it is GoalNotice.CalorieFloorApplied && it.floorKcal == 1500.0 })
    }

    @Test
    fun rule3_floorWithoutAnyDeficitGivesRateZeroAndNoProjection() {
        // TDEE 1112 < floor 1200: nothing can be lost safely
        val plan = GoalPlanner.plan(
            profile(Sex.FEMALE, age = 60, heightCm = 150.0, activity = ActivityLevel.SEDENTARY, goal = lose(0.5, 42.0)),
            weightKg = 45.0,
            today = TODAY,
            trendWeightKg = 45.0,
        )
        assertEquals(1200.0, plan.targetKcal, 1e-9)
        assertEquals(0.0, plan.effectiveWeeklyRateKg, 1e-9)
        assertEquals(GoalProgress.NoProjection, plan.progress)
        val notice = plan.notices.filterIsInstance<GoalNotice.CalorieFloorApplied>().single()
        assertEquals(0.0, notice.effectiveWeeklyRateKg, 1e-9)
    }

    @Test
    fun rule3_maintenanceNeverGoesBelowTheFloorEither() {
        val plan = GoalPlanner.plan(
            profile(Sex.FEMALE, age = 60, heightCm = 150.0, activity = ActivityLevel.SEDENTARY),
            weightKg = 45.0,
            today = TODAY,
        )
        assertEquals(1200.0, plan.targetKcal, 1e-9)
        assertTrue(plan.notices.any { it is GoalNotice.TargetRaisedToFloor && it.floorKcal == 1200.0 })
    }

    @Test
    fun rule4_loseIsNotOfferedBelowBmi185() {
        // 180 cm, 59 kg → BMI 18,2
        val plan = GoalPlanner.plan(profile(goal = lose(0.5, goalWeight = 55.0)), weightKg = 59.0, today = TODAY)
        assertEquals(GoalType.MAINTAIN, plan.goalType)
        assertEquals(plan.tdeeKcal, plan.targetKcal, 1e-9)
        val notice = plan.notices.filterIsInstance<GoalNotice.LoseNotOfferedBelowNormalBmi>().single()
        assertEquals(18.2, notice.bmi, 0.05)
    }

    @Test
    fun rule4_bmiExactly185IsStillAllowedToLose() {
        val plan = GoalPlanner.plan(
            profile(heightCm = 200.0, goal = lose(0.25, goalWeight = 74.0)),
            weightKg = 74.0 + 1e-9,
            today = TODAY,
        )
        assertEquals(GoalType.LOSE, plan.goalType)
    }

    @Test
    fun rule4_gainingIsAlwaysAllowedWhenUnderweight() {
        val plan = GoalPlanner.plan(profile(goal = gain(0.25, goalWeight = 65.0)), weightKg = 59.0, today = TODAY)
        assertEquals(GoalType.GAIN, plan.goalType)
        assertTrue(plan.notices.none { it is GoalNotice.LoseNotOfferedBelowNormalBmi })
    }

    // --- BMR formula --------------------------------------------------------------------------

    @Test
    fun katchMcArdleIsUsedWhenChosenAndBodyFatIsKnown() {
        val plan = GoalPlanner.plan(
            profile(formula = BmrFormula.KATCH_MCARDLE, bodyFat = 20.0),
            weightKg = 80.0,
            today = TODAY,
        )
        assertEquals(BmrFormula.KATCH_MCARDLE, plan.bmrFormulaUsed)
        assertEquals(1752.4, plan.bmrKcal, 1e-9)
    }

    @Test
    fun katchMcArdleWithoutBodyFatFallsBackToMifflinWithANotice() {
        val plan = GoalPlanner.plan(profile(formula = BmrFormula.KATCH_MCARDLE, bodyFat = null), 80.0, TODAY)
        assertEquals(BmrFormula.MIFFLIN_ST_JEOR, plan.bmrFormulaUsed)
        assertEquals(1780.0, plan.bmrKcal, 1e-9)
        assertTrue(GoalNotice.KatchMcArdleNeedsBodyFat in plan.notices)
    }

    // --- Macros in the plan ---------------------------------------------------------------------

    @Test
    fun macroTargetsFollowTheTargetAndCurrentWeight() {
        val plan = GoalPlanner.plan(profile(goal = lose(0.5)), weightKg = 80.0, today = TODAY)
        assertEquals(128.0, plan.macros.proteinG, 1e-9)
        assertEquals(plan.targetKcal, plan.macros.kcal, 1e-6)
    }

    @Test
    fun macroNoticesAreForwardedToThePlan() {
        // 80 kg × (3 g protein + 3 g fat) = 960 + 2160 = 3120 kcal, more than the 2759 kcal target
        val plan = GoalPlanner.plan(
            profile(macros = MacroSettings.PerKilogram(3.0, 3.0)),
            weightKg = 80.0,
            today = TODAY,
        )
        assertTrue(GoalNotice.MacrosExceedTarget in plan.notices)
        assertEquals(0.0, plan.macros.carbsG, 1e-9)
    }

    // --- Projection -------------------------------------------------------------------------

    @Test
    fun projectedDateIsDistanceOverWeeklyRate() {
        val progress = GoalPlanner.progress(GoalType.LOSE, goalWeightKg = 70.0, trendWeightKg = 80.0, weeklyRateKg = 0.5, today = TODAY)
        val projected = assertIs<GoalProgress.Projected>(progress)
        assertEquals(20.0, projected.weeks, 1e-9)
        assertEquals(TODAY.plusDays(140), projected.date)
    }

    @Test
    fun projectedDateRoundsUpToWholeDays() {
        // 3 kg at 0,5 kg/week → 6 weeks; 3,1 kg → 43,4 days → 44
        val p = GoalPlanner.progress(GoalType.GAIN, goalWeightKg = 83.1, trendWeightKg = 80.0, weeklyRateKg = 0.5, today = TODAY)
        assertEquals(TODAY.plusDays(44), assertIs<GoalProgress.Projected>(p).date)
    }

    @Test
    fun projectionUsesTheTrendWeightNotTheLatestEntry() {
        val plan = GoalPlanner.plan(
            profile(goal = lose(0.5, goalWeight = 70.0)),
            weightKg = 79.0,
            trendWeightKg = 80.0,
            today = TODAY,
        )
        val projected = assertIs<GoalProgress.Projected>(plan.progress)
        assertEquals(20.0, projected.weeks, 1e-9)
    }

    @Test
    fun goalReachedOrPassedIsReportedAsReached() {
        assertEquals(GoalProgress.Reached, GoalPlanner.progress(GoalType.LOSE, 70.0, 70.0, 0.5, TODAY))
        assertEquals(GoalProgress.Reached, GoalPlanner.progress(GoalType.LOSE, 70.0, 69.2, 0.5, TODAY))
        assertEquals(GoalProgress.Reached, GoalPlanner.progress(GoalType.GAIN, 85.0, 85.5, 0.5, TODAY))
    }

    @Test
    fun noProjectionWithoutGoalWeightOrRate() {
        assertEquals(GoalProgress.NoProjection, GoalPlanner.progress(GoalType.LOSE, null, 80.0, 0.5, TODAY))
        assertEquals(GoalProgress.NoProjection, GoalPlanner.progress(GoalType.LOSE, 70.0, 80.0, 0.0, TODAY))
        assertEquals(GoalProgress.NoProjection, GoalPlanner.progress(GoalType.MAINTAIN, 70.0, 80.0, 0.5, TODAY))
    }

    // --- Invariants over many profiles ----------------------------------------------------------------

    @Test
    fun safetyRulesHoldForEveryCombination() {
        var plans = 0
        for (sex in Sex.entries) for (age in listOf(18, 25, 40, 65, 90)) for (height in listOf(150.0, 165.0, 180.0, 195.0))
            for (weight in listOf(40.0, 50.0, 60.0, 75.0, 90.0, 120.0, 200.0)) for (activity in ActivityLevel.entries)
                for (goal in goals()) {
                    val plan = GoalPlanner.plan(profile(sex, age, height, activity, goal = goal), weight, TODAY)
                    plans++
                    val floor = GoalPlanner.calorieFloor(sex)
                    val context = "$sex $age y $height cm $weight kg $activity $goal"

                    assertTrue(plan.targetKcal >= floor - 1e-9, "target below floor: $context → ${plan.targetKcal}")
                    assertTrue(plan.effectiveWeeklyRateKg >= 0.0, "negative rate: $context")
                    assertEquals(plan.targetKcal - plan.tdeeKcal, plan.dailyEnergyChangeKcal, 1e-9, context)

                    when (plan.goalType) {
                        GoalType.LOSE -> {
                            assertTrue(plan.effectiveWeeklyRateKg <= 0.01 * weight + 1e-9, "rate above 1 %: $context")
                            assertTrue(plan.effectiveWeeklyRateKg <= goal.weeklyRateKg + 1e-9, "rate above wish: $context")
                            assertTrue(plan.bmi >= 18.5, "LOSE planned with BMI ${plan.bmi}: $context")
                            assertEquals(
                                maxOf(0.0, plan.tdeeKcal - plan.targetKcal) / 1100.0,
                                plan.effectiveWeeklyRateKg,
                                1e-9,
                                "rate does not match the deficit: $context",
                            )
                        }
                        GoalType.MAINTAIN -> {
                            assertEquals(maxOf(plan.tdeeKcal, floor), plan.targetKcal, 1e-9, context)
                            assertEquals(0.0, plan.effectiveWeeklyRateKg, 0.0, context)
                        }
                        GoalType.GAIN -> assertTrue(plan.targetKcal >= plan.tdeeKcal - 1e-9, "gain below TDEE: $context")
                    }
                    if (goal.type != GoalType.MAINTAIN && plan.goalType == GoalType.MAINTAIN) {
                        assertTrue(plan.notices.isNotEmpty(), "silent downgrade to maintenance: $context")
                    }
                }
        assertTrue(plans > 5000, "sweep too small: $plans")
    }

    private fun goals(): List<Goal> =
        GoalPlanner.LOSE_RATES_KG.map { lose(it) } + GoalPlanner.GAIN_RATES_KG.map { gain(it) } + Goal.MAINTAIN

    @Test
    fun minorsNeverGetADeficitInTheSweep() {
        for (age in 10..17) for (rate in GoalPlanner.LOSE_RATES_KG) {
            val plan = GoalPlanner.plan(profile(age = age, goal = lose(rate)), 60.0, TODAY)
            assertEquals(GoalType.MAINTAIN, plan.goalType, "age $age")
        }
    }
}
