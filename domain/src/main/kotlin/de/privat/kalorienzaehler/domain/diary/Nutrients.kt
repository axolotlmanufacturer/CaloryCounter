package de.privat.kalorienzaehler.domain.diary

/**
 * Nutrition values per 100 g or 100 ml. A value that is not known is `null`: it is shown as "–" and
 * never guessed or filled in.
 */
data class NutrientsPer100(
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugar: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
) {
    /** The values for [grams] of the food (1 ml counts as 1 g). */
    fun forGrams(grams: Double): NutrientAmounts {
        val factor = grams / 100.0
        return NutrientAmounts(
            kcal = kcal * factor,
            protein = protein?.times(factor),
            carbs = carbs?.times(factor),
            fat = fat?.times(factor),
        )
    }
}

/**
 * The four values a diary entry snapshots when it is logged. Macros are `null` when unknown
 * (a quick entry may only know the calories).
 */
data class NutrientAmounts(
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
) {
    /** Protein, carbs and fat are all known. */
    val hasAllMacros: Boolean get() = protein != null && carbs != null && fat != null

    operator fun times(factor: Double): NutrientAmounts = NutrientAmounts(
        kcal = kcal * factor,
        protein = protein?.times(factor),
        carbs = carbs?.times(factor),
        fat = fat?.times(factor),
    )
}
