package de.privat.kalorienzaehler.domain.model

import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import java.time.LocalDate

/** Sex as used by the BMR formulas ("für die Formel"). */
enum class Sex { MALE, FEMALE }

/** Activity level and its TDEE factor (PROMPT.md §6.2). */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    HIGH(1.725),
    EXTREME(1.9),
}

enum class BmrFormula { MIFFLIN_ST_JEOR, KATCH_MCARDLE }

enum class GoalType { LOSE, MAINTAIN, GAIN }

/**
 * The user's weight goal. [weeklyRateKg] is the wished change per week as a positive number
 * (direction comes from [type]); [weightKg] is the goal weight and is required for LOSE and GAIN.
 */
data class Goal(
    val type: GoalType,
    val weeklyRateKg: Double,
    val weightKg: Double?,
) {
    companion object {
        val MAINTAIN = Goal(GoalType.MAINTAIN, weeklyRateKg = 0.0, weightKg = null)
    }
}

/** Everything the calculators need to know about the person; current weight comes separately. */
data class UserProfile(
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val activityLevel: ActivityLevel,
    val bodyFatPercent: Double?,
    val formula: BmrFormula,
    val goal: Goal,
    val macros: MacroSettings,
    val energyUnit: EnergyUnit,
)
