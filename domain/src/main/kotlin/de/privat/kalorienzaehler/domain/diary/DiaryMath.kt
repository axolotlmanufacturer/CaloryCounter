package de.privat.kalorienzaehler.domain.diary

/**
 * Sums of some diary entries. Macros add up only the values that are known; [entriesWithoutAllMacros]
 * says how many entries were missing at least one of them, so the UI can say the macro sums are incomplete.
 */
data class DiaryTotals(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val entryCount: Int,
    val entriesWithoutAllMacros: Int,
) {
    companion object {
        val EMPTY = DiaryTotals(0.0, 0.0, 0.0, 0.0, 0, 0)
    }
}

/** One meal of a day with its entries (oldest first) and their sums. */
data class MealSection(
    val meal: Meal,
    val entries: List<DiaryEntry>,
    val totals: DiaryTotals,
)

/** Eaten versus target: the numbers behind the ring (Ziel − Gegessen = Übrig). */
data class EnergyBalance(val targetKcal: Double, val eatenKcal: Double) {
    val remainingKcal: Double get() = targetKcal - eatenKcal

    val isOver: Boolean get() = eatenKcal > targetKcal

    /** Share of the target that has been eaten, 0..1 for the progress ring. */
    val fraction: Double
        get() = if (targetKcal <= 0.0) 0.0 else (eatenKcal / targetKcal).coerceIn(0.0, 1.0)
}

/** Progress of one macronutrient towards its daily target. */
data class MacroProgress(val eatenG: Double, val targetG: Double) {
    val isOver: Boolean get() = eatenG > targetG

    val fraction: Double
        get() = if (targetG <= 0.0) 0.0 else (eatenG / targetG).coerceIn(0.0, 1.0)
}

object DiaryMath {

    fun totals(entries: List<DiaryEntry>): DiaryTotals {
        var kcal = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        var incomplete = 0
        for (entry in entries) {
            val nutrients = entry.nutrients
            kcal += nutrients.kcal
            protein += nutrients.protein ?: 0.0
            carbs += nutrients.carbs ?: 0.0
            fat += nutrients.fat ?: 0.0
            if (!nutrients.hasAllMacros) incomplete++
        }
        return DiaryTotals(kcal, protein, carbs, fat, entries.size, incomplete)
    }

    /** All four meals in display order, empty ones included; entries inside a meal oldest first. */
    fun sections(entries: List<DiaryEntry>): List<MealSection> {
        val byMeal = entries.groupBy { it.meal }
        return Meal.entries.map { meal ->
            val mealEntries = byMeal[meal].orEmpty().sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
            MealSection(meal, mealEntries, totals(mealEntries))
        }
    }
}
