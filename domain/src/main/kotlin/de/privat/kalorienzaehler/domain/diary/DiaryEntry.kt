package de.privat.kalorienzaehler.domain.diary

import java.time.LocalDate

/**
 * One logged food. [nutrients] are a snapshot taken when the entry was logged, so history never
 * changes when the food database is updated.
 *
 * [amount] is measured in [unit] (`"g"`, `"ml"`, `"Portion"` or a portion label); [grams] is the
 * weight that amount stands for. For [FoodSource.QUICK] entries there is no amount: [grams] is 0
 * ("unknown") and [amount] is 1.
 */
data class DiaryEntry(
    val id: Long = 0,
    val date: LocalDate,
    val meal: Meal,
    val source: FoodSource,
    val foodRef: String?,
    val name: String,
    val brand: String?,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val nutrients: NutrientAmounts,
    val createdAtMillis: Long,
) {
    val isQuick: Boolean get() = source == FoodSource.QUICK

    /**
     * The same food in another amount of the same unit. Weight and nutrients scale proportionally,
     * so the entry stays consistent even if the food has since been changed or deleted.
     */
    fun withAmount(newAmount: Double): DiaryEntry {
        require(!isQuick) { "A quick entry has no amount" }
        require(amount > 0.0 && newAmount > 0.0) { "Amounts must be positive" }
        val factor = newAmount / amount
        return copy(amount = newAmount, grams = grams * factor, nutrients = nutrients * factor)
    }

    /** A new, not yet stored entry for [targetDate] (e.g. "copy from yesterday"). */
    fun copiedTo(targetDate: LocalDate, createdAtMillis: Long): DiaryEntry =
        copy(id = 0, date = targetDate, createdAtMillis = createdAtMillis)

    /** Values per 100 g derived from the snapshot; `null` for quick entries (weight unknown). */
    fun per100(): NutrientsPer100? {
        if (isQuick || grams <= 0.0) return null
        val factor = 100.0 / grams
        return NutrientsPer100(
            kcal = nutrients.kcal * factor,
            protein = nutrients.protein?.times(factor),
            carbs = nutrients.carbs?.times(factor),
            fat = nutrients.fat?.times(factor),
        )
    }
}

/** A food the user entered by hand, with the values as printed on the pack. */
data class CustomFood(
    val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val nutrients: NutrientsPer100,
    val perUnit: PerUnit,
    /** Weight of one portion in g (or ml), if known. */
    val servingG: Double?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
