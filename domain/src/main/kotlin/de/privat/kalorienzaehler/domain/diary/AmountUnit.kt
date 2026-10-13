package de.privat.kalorienzaehler.domain.diary

/**
 * A way to state an amount of a food. [key] is what a diary entry stores as its unit: `"g"`, `"ml"`,
 * `"Portion"` or, later, the label of a typical portion. [gramsPerUnit] converts to grams (1 ml = 1 g).
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
        const val PORTION = "Portion"

        /** g or ml, plus "Portion" when the pack states a portion size. */
        fun forFood(perUnit: PerUnit, servingG: Double?): List<AmountUnit> =
            listOfNotNull(
                AmountUnit(perUnit.code, 1.0),
                servingG?.let { AmountUnit(PORTION, it) },
            )

        /** The unit an existing entry was logged in, reconstructed from its snapshot. */
        fun of(entry: DiaryEntry): AmountUnit = AmountUnit(entry.unit, entry.grams / entry.amount)
    }
}
