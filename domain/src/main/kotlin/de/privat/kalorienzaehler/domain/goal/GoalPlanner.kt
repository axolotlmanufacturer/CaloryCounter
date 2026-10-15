package de.privat.kalorienzaehler.domain.goal

import de.privat.kalorienzaehler.domain.body.Bmr
import de.privat.kalorienzaehler.domain.body.BodyMetrics
import de.privat.kalorienzaehler.domain.body.Tdee
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.nutrition.MacroCalculator
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

/**
 * Turns a profile and the current weight into the daily calorie target (PROMPT.md §6.3) and
 * enforces the safety rules of §6.4:
 *
 * 1. Under 18 there is no weight goal (and the numbers come with a caveat: the formulas are not made for minors).
 * 2. The weekly loss rate is capped at 1 % of the current body weight.
 * 3. The target never falls below 1.200 kcal (female) / 1.500 kcal (male); the real rate is recomputed.
 * 4. With a BMI below 18.5 "Abnehmen" is not offered (the plan falls back to maintenance).
 *
 * Whenever a rule changes what the user asked for, a [GoalNotice] says so.
 */
object GoalPlanner {

    /** Energy equivalent of 1 kg body weight change. */
    const val KCAL_PER_KG: Double = 7700.0
    const val MAX_LOSS_SHARE_OF_BODY_WEIGHT_PER_WEEK: Double = 0.01
    const val MIN_AGE_FOR_GOAL: Int = 18
    const val CALORIE_FLOOR_FEMALE: Double = 1200.0
    const val CALORIE_FLOOR_MALE: Double = 1500.0

    /** Weekly rates offered in the UI (kg per week). */
    val LOSE_RATES_KG: List<Double> = listOf(0.25, 0.5, 0.75, 1.0)
    val GAIN_RATES_KG: List<Double> = listOf(0.25, 0.5)

    private const val RATE_EPSILON = 1e-9

    fun calorieFloor(sex: Sex): Double = when (sex) {
        Sex.FEMALE -> CALORIE_FLOOR_FEMALE
        Sex.MALE -> CALORIE_FLOOR_MALE
    }

    /** Daily energy that corresponds to a weekly weight change of [kgPerWeek]. */
    fun dailyEnergyForWeeklyRate(kgPerWeek: Double): Double = kgPerWeek * KCAL_PER_KG / 7.0

    /**
     * @param weightKg current weight (latest entry); used for BMR, BMI, the 1 % cap and g/kg macros
     * @param trendWeightKg trend weight; used for the projected goal date
     */
    fun plan(
        profile: UserProfile,
        weightKg: Double,
        today: LocalDate,
        trendWeightKg: Double = weightKg,
    ): GoalPlan {
        val notices = mutableListOf<GoalNotice>()
        val ageYears = BodyMetrics.ageYears(profile.birthDate, today)
        val bmi = BodyMetrics.bmi(weightKg, profile.heightCm)

        val formulaUsed = effectiveFormula(profile, notices)
        val bmr = when (formulaUsed) {
            BmrFormula.MIFFLIN_ST_JEOR -> Bmr.mifflinStJeor(profile.sex, weightKg, profile.heightCm, ageYears)
            BmrFormula.KATCH_MCARDLE -> Bmr.katchMcArdle(weightKg, checkNotNull(profile.bodyFatPercent))
        }
        val tdee = Tdee.of(bmr, profile.activityLevel)

        if (ageYears < MIN_AGE_FOR_GOAL) notices += GoalNotice.EstimateNotMadeForMinors

        var goalType = profile.goal.type
        if (goalType != GoalType.MAINTAIN && ageYears < MIN_AGE_FOR_GOAL) {
            goalType = GoalType.MAINTAIN
            notices += GoalNotice.GoalRequiresAdult
        } else if (goalType == GoalType.LOSE && bmi < BodyMetrics.MIN_NORMAL_BMI) {
            goalType = GoalType.MAINTAIN
            notices += GoalNotice.LoseNotOfferedBelowNormalBmi(bmi)
        }

        val floor = calorieFloor(profile.sex)
        var targetKcal = tdee
        var weeklyRateKg = 0.0

        when (goalType) {
            GoalType.MAINTAIN -> Unit
            GoalType.LOSE -> {
                val requested = profile.goal.weeklyRateKg.coerceIn(0.0, LOSE_RATES_KG.max())
                val cap = MAX_LOSS_SHARE_OF_BODY_WEIGHT_PER_WEEK * weightKg
                weeklyRateKg = min(requested, cap)
                if (requested - weeklyRateKg > RATE_EPSILON) {
                    notices += GoalNotice.RateCappedToOnePercent(requestedKg = requested, cappedKg = weeklyRateKg)
                }
                targetKcal = tdee - dailyEnergyForWeeklyRate(weeklyRateKg)
                if (targetKcal < floor) {
                    targetKcal = floor
                    weeklyRateKg = maxOf(0.0, (tdee - floor) * 7.0 / KCAL_PER_KG)
                    notices += GoalNotice.CalorieFloorApplied(floor, weeklyRateKg)
                }
            }
            GoalType.GAIN -> {
                weeklyRateKg = profile.goal.weeklyRateKg.coerceIn(0.0, GAIN_RATES_KG.max())
                targetKcal = tdee + dailyEnergyForWeeklyRate(weeklyRateKg)
            }
        }
        if (goalType != GoalType.LOSE && targetKcal < floor) {
            targetKcal = floor
            notices += GoalNotice.TargetRaisedToFloor(floor)
        }

        val macros = MacroCalculator.targets(profile.macros, targetKcal, weightKg)
        if (macros.proteinReduced) notices += GoalNotice.ProteinReducedToFitTarget
        if (macros.exceedsCalorieTarget) notices += GoalNotice.MacrosExceedTarget

        return GoalPlan(
            ageYears = ageYears,
            bmi = bmi,
            bmrFormulaUsed = formulaUsed,
            bmrKcal = bmr,
            tdeeKcal = tdee,
            goalType = goalType,
            targetKcal = targetKcal,
            dailyEnergyChangeKcal = targetKcal - tdee,
            effectiveWeeklyRateKg = weeklyRateKg,
            macros = macros,
            progress = progress(goalType, profile.goal.weightKg, trendWeightKg, weeklyRateKg, today),
            notices = notices,
        )
    }

    private fun effectiveFormula(profile: UserProfile, notices: MutableList<GoalNotice>): BmrFormula =
        if (profile.formula == BmrFormula.KATCH_MCARDLE && profile.bodyFatPercent == null) {
            notices += GoalNotice.KatchMcArdleNeedsBodyFat
            BmrFormula.MIFFLIN_ST_JEOR
        } else {
            profile.formula
        }

    /** Projected date = today + `|trend − goal| ÷ weekly rate` (§6.3). */
    fun progress(
        goalType: GoalType,
        goalWeightKg: Double?,
        trendWeightKg: Double,
        weeklyRateKg: Double,
        today: LocalDate,
    ): GoalProgress {
        if (goalType == GoalType.MAINTAIN || goalWeightKg == null) return GoalProgress.NoProjection
        val reached = when (goalType) {
            GoalType.LOSE -> trendWeightKg <= goalWeightKg
            GoalType.GAIN -> trendWeightKg >= goalWeightKg
            GoalType.MAINTAIN -> false
        }
        if (reached) return GoalProgress.Reached
        if (weeklyRateKg <= RATE_EPSILON) return GoalProgress.NoProjection
        val weeks = kotlin.math.abs(trendWeightKg - goalWeightKg) / weeklyRateKg
        return GoalProgress.Projected(date = today.plusDays(ceil(weeks * 7.0).toLong()), weeks = weeks)
    }
}
