package de.privat.kalorienzaehler.domain.profile

import de.privat.kalorienzaehler.domain.body.BodyMetrics
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.format.DecimalInput
import de.privat.kalorienzaehler.domain.format.GermanDate
import de.privat.kalorienzaehler.domain.format.GermanFormat
import de.privat.kalorienzaehler.domain.goal.BirthDateIssue
import de.privat.kalorienzaehler.domain.goal.GoalPlan
import de.privat.kalorienzaehler.domain.goal.GoalPlanner
import de.privat.kalorienzaehler.domain.goal.GoalWeightIssue
import de.privat.kalorienzaehler.domain.goal.InputLimits
import de.privat.kalorienzaehler.domain.goal.ProfileValidation
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import java.time.LocalDate

enum class MacroMode { DEFAULT, PERCENT, PER_KG }

enum class DraftField { SEX, BIRTH_DATE, HEIGHT, WEIGHT, ACTIVITY, BODY_FAT, FORMULA, GOAL_WEIGHT, MACROS }

sealed interface DraftError {
    /** Nothing entered or selected yet. */
    data object Required : DraftError

    /** Text that is not a number or date. */
    data object Unreadable : DraftError

    /** Outside the plausible range (typo guard). */
    data object OutOfRange : DraftError

    data class BirthDate(val issue: BirthDateIssue) : DraftError

    data class GoalWeight(val issue: GoalWeightIssue) : DraftError

    /** Katch-McArdle needs the body fat percentage. */
    data object BodyFatRequiredForKatchMcArdle : DraftError

    data object PercentsMustSumTo100 : DraftError

    data object MacroValuesUnreadable : DraftError
}

/** What [ProfileDraft.evaluate] found out about a draft. */
data class DraftEvaluation(
    val errors: Map<DraftField, DraftError>,
    /** The finished profile; `null` while there are errors. */
    val profile: UserProfile?,
    val weightKg: Double?,
    /**
     * Live preview of the plan. Available as soon as the body data is valid, even if goal weight
     * or macro inputs are still incomplete (those parts then fall back to defaults).
     */
    val plan: GoalPlan?,
    val ageYears: Int?,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Editable form state shared by onboarding and the Profil screen. Everything the user types is kept
 * as text so half-finished input survives; [evaluate] parses and validates it (German formats:
 * `72,4`, `13.10.1990`).
 */
data class ProfileDraft(
    val sex: Sex? = null,
    val birthDate: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val activity: ActivityLevel? = null,
    val bodyFatPercent: String = "",
    val formula: BmrFormula = BmrFormula.MIFFLIN_ST_JEOR,
    val goalType: GoalType = GoalType.MAINTAIN,
    val weeklyRateKg: Double = DEFAULT_LOSE_RATE_KG,
    val goalWeightKg: String = "",
    val macroMode: MacroMode = MacroMode.DEFAULT,
    val proteinPercent: String = "30",
    val carbsPercent: String = "40",
    val fatPercent: String = "30",
    val proteinGPerKg: String = "1,6",
    val fatGPerKg: String = "1",
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
) {

    /** Switches the goal type and makes sure the weekly rate is one the new type offers. */
    fun withGoalType(type: GoalType): ProfileDraft = copy(
        goalType = type,
        weeklyRateKg = when (type) {
            GoalType.LOSE -> weeklyRateKg.takeIf { it in GoalPlanner.LOSE_RATES_KG } ?: DEFAULT_LOSE_RATE_KG
            GoalType.GAIN -> weeklyRateKg.takeIf { it in GoalPlanner.GAIN_RATES_KG } ?: DEFAULT_GAIN_RATE_KG
            GoalType.MAINTAIN -> weeklyRateKg
        },
    )

    fun evaluate(today: LocalDate): DraftEvaluation {
        val errors = LinkedHashMap<DraftField, DraftError>()

        if (sex == null) errors[DraftField.SEX] = DraftError.Required
        val birth = readBirthDate(today, errors)
        val height = readNumber(heightCm, InputLimits.HEIGHT_CM, DraftField.HEIGHT, errors)
        val weight = readNumber(weightKg, InputLimits.WEIGHT_KG, DraftField.WEIGHT, errors)
        if (activity == null) errors[DraftField.ACTIVITY] = DraftError.Required
        val bodyFat = if (bodyFatPercent.isBlank()) {
            null
        } else {
            readNumber(bodyFatPercent, InputLimits.BODY_FAT_PERCENT, DraftField.BODY_FAT, errors)
        }
        if (formula == BmrFormula.KATCH_MCARDLE && bodyFat == null) {
            errors[DraftField.FORMULA] = DraftError.BodyFatRequiredForKatchMcArdle
        }

        val ageYears = birth?.let { BodyMetrics.ageYears(it, today) }
        val goalWeight = readGoalWeight(ageYears, weight, height, errors)
        val macros = readMacros(errors)

        val baseValid = sex != null && birth != null && DraftField.BIRTH_DATE !in errors &&
            height != null && weight != null && activity != null
        // Incomplete goal or macro input falls back to defaults so the preview keeps working;
        // once there are no errors this is exactly the profile that was entered.
        val profileOrPreview = if (baseValid) {
            UserProfile(
                sex = sex,
                birthDate = birth,
                heightCm = height,
                activityLevel = activity,
                bodyFatPercent = bodyFat,
                formula = formula,
                goal = Goal(goalType, rateFor(goalType), goalWeight),
                macros = macros ?: MacroSettings.Default,
                energyUnit = energyUnit,
            )
        } else {
            null
        }
        val plan = profileOrPreview?.let { GoalPlanner.plan(it, weightKg = weight!!, today = today) }
        val profile = profileOrPreview.takeIf { errors.isEmpty() }
        return DraftEvaluation(errors, profile, weight, plan, ageYears)
    }

    private fun rateFor(type: GoalType): Double = if (type == GoalType.MAINTAIN) 0.0 else weeklyRateKg

    private fun readBirthDate(today: LocalDate, errors: MutableMap<DraftField, DraftError>): LocalDate? {
        if (birthDate.isBlank()) {
            errors[DraftField.BIRTH_DATE] = DraftError.Required
            return null
        }
        val date = GermanDate.parse(birthDate)
        if (date == null) {
            errors[DraftField.BIRTH_DATE] = DraftError.Unreadable
            return null
        }
        ProfileValidation.birthDate(date, today)?.let { errors[DraftField.BIRTH_DATE] = DraftError.BirthDate(it) }
        return date
    }

    private fun readNumber(
        text: String,
        range: ClosedFloatingPointRange<Double>,
        field: DraftField,
        errors: MutableMap<DraftField, DraftError>,
    ): Double? {
        if (text.isBlank()) {
            errors[field] = DraftError.Required
            return null
        }
        val value = DecimalInput.parse(text)
        if (value == null) {
            errors[field] = DraftError.Unreadable
            return null
        }
        if (value !in range) {
            errors[field] = DraftError.OutOfRange
            return null
        }
        return value
    }

    /**
     * The goal weight is only checked while a weight goal really applies: not for adults under 18
     * and not for "Abnehmen" below BMI 18,5 (the planner turns those into maintenance and says so).
     */
    private fun readGoalWeight(
        ageYears: Int?,
        weight: Double?,
        height: Double?,
        errors: MutableMap<DraftField, DraftError>,
    ): Double? {
        val parsed = if (goalWeightKg.isBlank()) null else DecimalInput.parse(goalWeightKg)
        if (goalType == GoalType.MAINTAIN || ageYears == null || weight == null || height == null) return parsed
        val goalApplies = ProfileValidation.isGoalAllowed(ageYears) &&
            (goalType != GoalType.LOSE || BodyMetrics.bmi(weight, height) >= BodyMetrics.MIN_NORMAL_BMI)
        if (!goalApplies) return parsed
        if (goalWeightKg.isNotBlank() && parsed == null) {
            errors[DraftField.GOAL_WEIGHT] = DraftError.Unreadable
            return null
        }
        ProfileValidation.goalWeight(goalType, parsed, weight, height)?.let {
            errors[DraftField.GOAL_WEIGHT] = DraftError.GoalWeight(it)
        }
        return parsed
    }

    private fun readMacros(errors: MutableMap<DraftField, DraftError>): MacroSettings? = when (macroMode) {
        MacroMode.DEFAULT -> MacroSettings.Default
        MacroMode.PERCENT -> {
            val values = listOf(proteinPercent, carbsPercent, fatPercent).map(DecimalInput::parse)
            if (values.any { it == null || it < 0.0 || it > 100.0 }) {
                errors[DraftField.MACROS] = DraftError.MacroValuesUnreadable
                null
            } else {
                MacroSettings.PercentSplit(values[0]!!, values[1]!!, values[2]!!).also {
                    if (!it.isValid) errors[DraftField.MACROS] = DraftError.PercentsMustSumTo100
                }.takeIf { it.isValid }
            }
        }
        MacroMode.PER_KG -> {
            val values = listOf(proteinGPerKg, fatGPerKg).map(DecimalInput::parse)
            if (values.any { it == null || it > MAX_G_PER_KG }) {
                errors[DraftField.MACROS] = DraftError.MacroValuesUnreadable
                null
            } else {
                MacroSettings.PerKilogram(values[0]!!, values[1]!!)
            }
        }
    }

    companion object {
        const val DEFAULT_LOSE_RATE_KG: Double = 0.5
        const val DEFAULT_GAIN_RATE_KG: Double = 0.25
        private const val MAX_G_PER_KG: Double = 10.0

        /** Draft for editing an existing profile. */
        fun from(profile: UserProfile, currentWeightKg: Double): ProfileDraft {
            val goal = profile.goal
            val macros = profile.macros
            return ProfileDraft(
                sex = profile.sex,
                birthDate = GermanFormat.date(profile.birthDate),
                heightCm = GermanFormat.upTo(profile.heightCm, 1),
                weightKg = GermanFormat.upTo(currentWeightKg, 1),
                activity = profile.activityLevel,
                bodyFatPercent = profile.bodyFatPercent?.let { GermanFormat.upTo(it, 1) }.orEmpty(),
                formula = profile.formula,
                goalType = goal.type,
                weeklyRateKg = if (goal.type == GoalType.MAINTAIN) DEFAULT_LOSE_RATE_KG else goal.weeklyRateKg,
                goalWeightKg = goal.weightKg?.let { GermanFormat.upTo(it, 1) }.orEmpty(),
                macroMode = when (macros) {
                    MacroSettings.Default -> MacroMode.DEFAULT
                    is MacroSettings.PercentSplit -> MacroMode.PERCENT
                    is MacroSettings.PerKilogram -> MacroMode.PER_KG
                },
                proteinPercent = (macros as? MacroSettings.PercentSplit)?.let { GermanFormat.upTo(it.proteinPercent, 1) } ?: "30",
                carbsPercent = (macros as? MacroSettings.PercentSplit)?.let { GermanFormat.upTo(it.carbsPercent, 1) } ?: "40",
                fatPercent = (macros as? MacroSettings.PercentSplit)?.let { GermanFormat.upTo(it.fatPercent, 1) } ?: "30",
                proteinGPerKg = (macros as? MacroSettings.PerKilogram)?.let { GermanFormat.upTo(it.proteinGPerKg, 2) } ?: "1,6",
                fatGPerKg = (macros as? MacroSettings.PerKilogram)?.let { GermanFormat.upTo(it.fatGPerKg, 2) } ?: "1",
                energyUnit = profile.energyUnit,
            )
        }
    }
}
