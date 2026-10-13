package de.privat.kalorienzaehler.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.body.BodyMetrics
import de.privat.kalorienzaehler.domain.energy.Energy
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.format.GermanFormat
import de.privat.kalorienzaehler.domain.goal.BirthDateIssue
import de.privat.kalorienzaehler.domain.goal.GoalNotice
import de.privat.kalorienzaehler.domain.goal.GoalWeightIssue
import de.privat.kalorienzaehler.domain.profile.DraftError
import de.privat.kalorienzaehler.domain.profile.DraftField

// Value formatting: numbers are formatted in German, the unit words come from string resources.

/** `2.209 kcal` or, in kJ mode, `9.243 kJ`. */
@Composable
fun energyText(kcal: Double, unit: EnergyUnit): String = when (unit) {
    EnergyUnit.KCAL -> stringResource(R.string.unit_kcal_value, GermanFormat.wholeNumber(kcal))
    EnergyUnit.KJ -> stringResource(R.string.unit_kj_value, GermanFormat.wholeNumber(Energy.kcalToKj(kcal)))
}

/** Always kcal, for the rule texts that name the 1.200/1.500 kcal floor. */
@Composable
fun kcalText(kcal: Double): String = stringResource(R.string.unit_kcal_value, GermanFormat.wholeNumber(kcal))

@Composable
fun kgText(kg: Double): String = stringResource(R.string.unit_kg_value, GermanFormat.decimal(kg, 1))

@Composable
fun gramsText(grams: Double): String = stringResource(R.string.unit_g_value, GermanFormat.wholeNumber(grams))

/** The friendly explanation for a safety-rule notice (PROMPT.md §6.4): neutral tone, never blaming. */
@Composable
fun GoalNotice.messageText(): String = when (this) {
    GoalNotice.EstimateNotMadeForMinors -> stringResource(R.string.notice_minor_estimate)
    GoalNotice.GoalRequiresAdult -> stringResource(R.string.notice_goal_requires_adult)
    is GoalNotice.LoseNotOfferedBelowNormalBmi ->
        stringResource(R.string.notice_lose_not_offered, GermanFormat.decimal(bmi, 1))
    is GoalNotice.RateCappedToOnePercent -> stringResource(
        R.string.notice_rate_capped,
        GermanFormat.upTo(requestedKg, 2),
        GermanFormat.upTo(cappedKg, 2),
    )
    is GoalNotice.CalorieFloorApplied ->
        if (effectiveWeeklyRateKg >= 0.005) {
            stringResource(
                R.string.notice_floor_applied,
                GermanFormat.decimal(effectiveWeeklyRateKg, 2),
                GermanFormat.wholeNumber(floorKcal),
            )
        } else {
            stringResource(R.string.notice_floor_applied_no_loss, GermanFormat.wholeNumber(floorKcal))
        }
    is GoalNotice.TargetRaisedToFloor ->
        stringResource(R.string.notice_target_raised_to_floor, GermanFormat.wholeNumber(floorKcal))
    GoalNotice.KatchMcArdleNeedsBodyFat -> stringResource(R.string.notice_katch_needs_body_fat)
    GoalNotice.ProteinReducedToFitTarget -> stringResource(R.string.notice_protein_reduced)
    GoalNotice.MacrosExceedTarget -> stringResource(R.string.notice_macros_exceed_target)
}

/**
 * The message shown under a field, or `null` when nothing should be said yet: an empty required
 * field is not an error while the user has not typed anything, the continue button just stays off.
 */
@Composable
fun DraftError.messageText(field: DraftField): String? = when (this) {
    DraftError.Required -> null
    DraftError.Unreadable ->
        if (field == DraftField.BIRTH_DATE) stringResource(R.string.error_date) else stringResource(R.string.error_number)
    DraftError.OutOfRange -> when (field) {
        DraftField.HEIGHT -> stringResource(R.string.error_height_range)
        DraftField.BODY_FAT -> stringResource(R.string.error_body_fat_range)
        else -> stringResource(R.string.error_weight_range)
    }
    is DraftError.BirthDate -> when (issue) {
        BirthDateIssue.IN_FUTURE -> stringResource(R.string.error_birth_date_future)
        BirthDateIssue.TOO_YOUNG -> stringResource(R.string.error_birth_date_too_young)
        BirthDateIssue.TOO_OLD -> stringResource(R.string.error_birth_date_too_old)
    }
    is DraftError.GoalWeight -> when (val goalIssue = issue) {
        GoalWeightIssue.Missing -> null
        is GoalWeightIssue.BelowNormalBmi ->
            stringResource(R.string.error_goal_weight_below_bmi, GermanFormat.decimal(goalIssue.minWeightKg, 1))
        GoalWeightIssue.NotBelowCurrent -> stringResource(R.string.error_goal_weight_not_below)
        GoalWeightIssue.NotAboveCurrent -> stringResource(R.string.error_goal_weight_not_above)
    }
    DraftError.BodyFatRequiredForKatchMcArdle -> stringResource(R.string.error_katch_needs_body_fat)
    DraftError.PercentsMustSumTo100 -> stringResource(R.string.error_percents_sum)
    DraftError.MacroValuesUnreadable -> stringResource(R.string.error_macro_values)
}

/** BMI 18,5 reminder under the goal weight field. */
@Composable
fun minGoalWeightHint(heightCm: Double?): String? =
    heightCm?.let {
        stringResource(R.string.field_goal_weight_min_hint, GermanFormat.decimal(BodyMetrics.minWeightForNormalBmi(it), 1))
    }
