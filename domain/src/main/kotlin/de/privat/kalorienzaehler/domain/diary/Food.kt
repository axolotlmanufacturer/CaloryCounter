package de.privat.kalorienzaehler.domain.diary

/**
 * Identifies a food wherever it came from: [ref] is the BLS code, the Open Food Facts barcode or the id of
 * a custom food. It stays valid when the food database is rebuilt (unlike the database's row ids), which is
 * why diary entries and favorites store it.
 */
data class FoodKey(val source: FoodSource, val ref: String) {
    init {
        require(source != FoodSource.QUICK) { "A quick entry is not a food" }
    }
}

/** What a list of foods shows: enough to display a row and to open the amount screen. */
data class FoodHit(
    val key: FoodKey,
    val name: String,
    val brand: String?,
    val kcalPer100: Double,
    val perUnit: PerUnit,
)

/** A typical portion of a food ("Stück (mittel)" = 150 g). [estimated] weights are shown with "ca.". */
data class FoodPortion(val label: String, val grams: Double, val estimated: Boolean)

/** A food from the bundled database. */
data class Food(
    val id: Long,
    val key: FoodKey,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val nutrients: NutrientsPer100,
    val perUnit: PerUnit,
    val servingG: Double?,
    val servingLabel: String?,
    val popularity: Int,
) {
    fun toHit(): FoodHit = FoodHit(key, name, brand, nutrients.kcal, perUnit)
}

/** Everything the amount screen needs to know about a food, whatever its source. */
data class LoggableFood(
    val key: FoodKey,
    val name: String,
    val brand: String?,
    val nutrients: NutrientsPer100,
    val perUnit: PerUnit,
    val servingG: Double?,
    val portions: List<FoodPortion>,
) {
    /** g or ml first, then the pack's own portion, then the typical portions. */
    fun units(): List<AmountUnit> =
        AmountUnit.forFood(perUnit, servingG) +
            portions.map { AmountUnit(it.label, it.grams, estimated = it.estimated) }
}

fun CustomFood.toHit(): FoodHit =
    FoodHit(FoodKey(FoodSource.CUSTOM, id.toString()), name, brand, nutrients.kcal, perUnit)

fun CustomFood.toLoggable(): LoggableFood =
    LoggableFood(FoodKey(FoodSource.CUSTOM, id.toString()), name, brand, nutrients, perUnit, servingG, emptyList())

fun Food.toLoggable(portions: List<FoodPortion>): LoggableFood =
    LoggableFood(key, name, brand, nutrients, perUnit, servingG, portions)
