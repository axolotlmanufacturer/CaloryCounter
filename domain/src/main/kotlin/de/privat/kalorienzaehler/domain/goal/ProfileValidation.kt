package de.privat.kalorienzaehler.domain.goal

import de.privat.kalorienzaehler.domain.body.BodyMetrics
import de.privat.kalorienzaehler.domain.model.GoalType
import java.time.LocalDate

/** Plausible input ranges; they only catch typos, they are not medical limits. */
object InputLimits {
    val HEIGHT_CM: ClosedFloatingPointRange<Double> = 100.0..250.0
    val WEIGHT_KG: ClosedFloatingPointRange<Double> = 30.0..350.0
    val BODY_FAT_PERCENT: ClosedFloatingPointRange<Double> = 3.0..60.0
    val AGE_YEARS: IntRange = 10..110
}

enum class BirthDateIssue { IN_FUTURE, TOO_YOUNG, TOO_OLD }

sealed interface GoalWeightIssue {
    /** LOSE and GAIN need a goal weight. */
    data object Missing : GoalWeightIssue

    /** The goal must correspond to a BMI of at least 18,5 (PROMPT.md §6.4). */
    data class BelowNormalBmi(val minWeightKg: Double) : GoalWeightIssue

    data object NotBelowCurrent : GoalWeightIssue

    data object NotAboveCurrent : GoalWeightIssue
}

object ProfileValidation {

    fun birthDate(birthDate: LocalDate, today: LocalDate): BirthDateIssue? {
        val age = BodyMetrics.ageYears(birthDate, today)
        return when {
            birthDate.isAfter(today) -> BirthDateIssue.IN_FUTURE
            age < InputLimits.AGE_YEARS.first -> BirthDateIssue.TOO_YOUNG
            age > InputLimits.AGE_YEARS.last -> BirthDateIssue.TOO_OLD
            else -> null
        }
    }

    fun isHeightValid(heightCm: Double): Boolean = heightCm in InputLimits.HEIGHT_CM

    fun isWeightValid(weightKg: Double): Boolean = weightKg in InputLimits.WEIGHT_KG

    fun isBodyFatValid(percent: Double): Boolean = percent in InputLimits.BODY_FAT_PERCENT

    /** Weight goals are for adults only (§6.4 rule 1). */
    fun isGoalAllowed(ageYears: Int): Boolean = ageYears >= GoalPlanner.MIN_AGE_FOR_GOAL

    /** "Abnehmen" is offered to adults with a BMI of at least 18,5 (§6.4 rules 1 and 4). */
    fun isLoseOffered(ageYears: Int, weightKg: Double, heightCm: Double): Boolean =
        isGoalAllowed(ageYears) && BodyMetrics.bmi(weightKg, heightCm) >= BodyMetrics.MIN_NORMAL_BMI

    fun goalWeight(
        type: GoalType,
        goalWeightKg: Double?,
        currentWeightKg: Double,
        heightCm: Double,
    ): GoalWeightIssue? {
        if (type == GoalType.MAINTAIN) return null
        if (goalWeightKg == null) return GoalWeightIssue.Missing
        val minWeight = BodyMetrics.minWeightForNormalBmi(heightCm)
        return when {
            goalWeightKg < minWeight -> GoalWeightIssue.BelowNormalBmi(minWeight)
            type == GoalType.LOSE && goalWeightKg >= currentWeightKg -> GoalWeightIssue.NotBelowCurrent
            type == GoalType.GAIN && goalWeightKg <= currentWeightKg -> GoalWeightIssue.NotAboveCurrent
            else -> null
        }
    }
}
