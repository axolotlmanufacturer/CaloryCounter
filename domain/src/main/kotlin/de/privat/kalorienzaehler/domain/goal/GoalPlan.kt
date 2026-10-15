package de.privat.kalorienzaehler.domain.goal

import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.nutrition.MacroTargets
import java.time.LocalDate

/**
 * Something the user should be told about the plan. The domain only says what happened and with
 * which numbers; the app turns it into friendly German text (PROMPT.md §6.4: never block silently).
 */
sealed interface GoalNotice {

    /** Under 18: the formulas are not made for minors, so every number is only a rough orientation. */
    data object EstimateNotMadeForMinors : GoalNotice

    /** Under 18: a wished weight goal is ignored, the plan is maintenance only. */
    data object GoalRequiresAdult : GoalNotice

    /** BMI below 18.5: "Abnehmen" is not offered, the plan is maintenance. */
    data class LoseNotOfferedBelowNormalBmi(val bmi: Double) : GoalNotice

    /** The wished loss rate exceeded 1 % of body weight per week and was reduced. */
    data class RateCappedToOnePercent(val requestedKg: Double, val cappedKg: Double) : GoalNotice

    /** The target would fall below the calorie floor; it was raised and the real weekly rate is shown. */
    data class CalorieFloorApplied(val floorKcal: Double, val effectiveWeeklyRateKg: Double) : GoalNotice

    /** Maintenance or gain target was below the floor and was raised to it. */
    data class TargetRaisedToFloor(val floorKcal: Double) : GoalNotice

    /** Katch-McArdle was chosen but no body fat is known; Mifflin-St Jeor was used instead. */
    data object KatchMcArdleNeedsBodyFat : GoalNotice

    /** Protein had to be reduced below 1,6 g/kg so that protein and fat fit the target. */
    data object ProteinReducedToFitTarget : GoalNotice

    /** The chosen g/kg macros need more calories than the target. */
    data object MacrosExceedTarget : GoalNotice
}

/** Where the user stands relative to the goal weight. */
sealed interface GoalProgress {
    /** No goal weight or no weekly change, so no date can be named. */
    data object NoProjection : GoalProgress

    /** Goal weight reached (or passed). */
    data object Reached : GoalProgress

    /** Expected date, from `|trend − goal| ÷ weekly rate`. */
    data class Projected(val date: LocalDate, val weeks: Double) : GoalProgress
}

/** Result of [GoalPlanner.plan]. All energy values are kcal as `Double`; display rounds. */
data class GoalPlan(
    val ageYears: Int,
    val bmi: Double,
    val bmrFormulaUsed: BmrFormula,
    val bmrKcal: Double,
    val tdeeKcal: Double,
    /** The goal that is actually planned for (may differ from the wish, see [notices]). */
    val goalType: GoalType,
    val targetKcal: Double,
    /** `target − tdee`: negative is a deficit, positive a surplus. */
    val dailyEnergyChangeKcal: Double,
    /** Realistic weekly change in kg as a positive number (0 for maintenance). */
    val effectiveWeeklyRateKg: Double,
    val macros: MacroTargets,
    val progress: GoalProgress,
    val notices: List<GoalNotice>,
)
