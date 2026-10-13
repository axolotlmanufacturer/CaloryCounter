package de.privat.kalorienzaehler.domain.diary

import kotlin.math.abs
import kotlin.math.max

/**
 * Hints about nutrition values that do not add up. They are shown as warnings only: the user may
 * know better than the rule (alcohol, polyols and fibre have other energy values), so nothing is blocked.
 */
sealed interface FoodWarning {
    /** Energy and macros disagree by more than the usual leeway; [expectedKcal] is what the macros add up to. */
    data class KcalMacroMismatch(val expectedKcal: Double) : FoodWarning

    /** More than 900 kcal per 100 g is not possible (pure fat); often kJ were typed instead of kcal. */
    data object EnergyAboveMaximum : FoodWarning

    /** The nutrients add up to more than 100 g per 100 g. */
    data object NutrientsExceed100g : FoodWarning

    /** "davon Zucker" is larger than the carbohydrates it is part of. */
    data object SugarExceedsCarbs : FoodWarning
}

object FoodPlausibility {

    const val MAX_KCAL_PER_100: Double = 900.0

    private const val FIBER_KCAL_PER_G = 2.0
    private const val KCAL_SLACK = 40.0
    private const val RELATIVE_TOLERANCE = 0.25
    private const val MAX_TOTAL_G_PER_100 = 105.0
    private const val ROUNDING_G = 0.05

    /** Checks values as printed on a pack (per 100 g/ml). */
    fun check(nutrients: NutrientsPer100): List<FoodWarning> = buildList {
        if (nutrients.kcal > MAX_KCAL_PER_100) add(FoodWarning.EnergyAboveMaximum)

        val total = listOfNotNull(
            nutrients.protein,
            nutrients.carbs,
            nutrients.fat,
            nutrients.fiber,
            nutrients.salt,
        ).sum()
        if (total > MAX_TOTAL_G_PER_100) add(FoodWarning.NutrientsExceed100g)

        val carbs = nutrients.carbs
        val sugar = nutrients.sugar
        if (carbs != null && sugar != null && sugar > carbs + ROUNDING_G) add(FoodWarning.SugarExceedsCarbs)

        mismatch(nutrients.kcal, nutrients.protein, nutrients.carbs, nutrients.fat, nutrients.fiber)
            ?.let(::add)
    }

    /** Checks absolute amounts, e.g. a quick entry: only energy against macros. */
    fun checkAmounts(amounts: NutrientAmounts): List<FoodWarning> =
        listOfNotNull(mismatch(amounts.kcal, amounts.protein, amounts.carbs, amounts.fat, fiber = null))

    private fun mismatch(kcal: Double, protein: Double?, carbs: Double?, fat: Double?, fiber: Double?): FoodWarning? {
        if (protein == null || carbs == null || fat == null) return null
        val expected = protein * MacroKcal.PROTEIN + carbs * MacroKcal.CARBS + fat * MacroKcal.FAT +
            (fiber ?: 0.0) * FIBER_KCAL_PER_G
        val tolerance = max(KCAL_SLACK, RELATIVE_TOLERANCE * max(kcal, expected))
        return if (abs(kcal - expected) > tolerance) FoodWarning.KcalMacroMismatch(expected) else null
    }

    private object MacroKcal {
        const val PROTEIN = 4.0
        const val CARBS = 4.0
        const val FAT = 9.0
    }
}
