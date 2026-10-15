package de.privat.kalorienzaehler.domain.diary

import de.privat.kalorienzaehler.domain.format.GermanFormat

/**
 * A way to state an amount of a food. [key] is what a diary entry stores as its unit and what the
 * UI shows: `"g"`, `"ml"`, or a portion label like `"Portion (30 g)"`. [gramsPerUnit] converts to
 * grams (1 ml = 1 g).
 */
data class AmountUnit(
    val key: String,
    val gramsPerUnit: Double,
    /** The weight of this portion is an estimate; the UI marks it "ca.". */
    val estimated: Boolean = false,
) {
    val isBase: Boolean get() = key == PerUnit.GRAM.code || key == PerUnit.MILLILITER.code

    fun toGrams(amount: Double): Double = amount * gramsPerUnit

    companion object {
        /** The unit word stored for quick entries, which have no real amount. */
        const val PORTION = "Portion"

        /** `Portion (30 g)` / `Portion (250 ml)`: the label doubles as the stored unit. */
        fun portion(servingG: Double, perUnit: PerUnit): AmountUnit =
            AmountUnit("$PORTION (${GermanFormat.upTo(servingG, 1)} ${perUnit.code})", servingG)

        /** g or ml, plus a portion when the pack states a portion size. */
        fun forFood(perUnit: PerUnit, servingG: Double?): List<AmountUnit> =
            listOfNotNull(
                AmountUnit(perUnit.code, 1.0),
                servingG?.let { portion(it, perUnit) },
            )

        /** The unit an existing entry was logged in, reconstructed from its snapshot. */
        fun of(entry: DiaryEntry): AmountUnit = AmountUnit(entry.unit, entry.grams / entry.amount)
    }
}
