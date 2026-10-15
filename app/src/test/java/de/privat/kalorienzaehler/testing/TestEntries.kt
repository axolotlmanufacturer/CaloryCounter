package de.privat.kalorienzaehler.testing

import de.privat.kalorienzaehler.domain.diary.AmountUnit
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Food
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.diary.NutrientsPer100
import de.privat.kalorienzaehler.domain.diary.PerUnit
import java.time.LocalDate

/** A logged custom food: [grams] of something with 200 kcal / 10 g protein / 30 g carbs / 5 g fat per 100 g. */
fun testEntry(
    id: Long = 0,
    date: LocalDate = TEST_TODAY,
    meal: Meal = Meal.FRUEHSTUECK,
    name: String = "Haferbrei",
    brand: String? = null,
    grams: Double = 150.0,
    createdAtMillis: Long = 1,
): DiaryEntry = DiaryEntry(
    id = id,
    date = date,
    meal = meal,
    source = FoodSource.CUSTOM,
    foodRef = "1",
    name = name,
    brand = brand,
    amount = grams,
    unit = "g",
    grams = grams,
    nutrients = NutrientsPer100(200.0, 10.0, 30.0, null, 5.0).forGrams(grams),
    createdAtMillis = createdAtMillis,
)

fun testQuickEntry(
    id: Long = 0,
    date: LocalDate = TEST_TODAY,
    meal: Meal = Meal.SNACKS,
    name: String = "Schnelleintrag",
    nutrients: NutrientAmounts = NutrientAmounts(250.0),
    createdAtMillis: Long = 1,
): DiaryEntry = DiaryEntry(
    id = id,
    date = date,
    meal = meal,
    source = FoodSource.QUICK,
    foodRef = null,
    name = name,
    brand = null,
    amount = 1.0,
    unit = AmountUnit.PORTION,
    grams = 0.0,
    nutrients = nutrients,
    createdAtMillis = createdAtMillis,
)

fun testFood(
    id: Long = 0,
    name: String = "Haferflocken",
    brand: String? = "Kölln",
    kcal: Double = 372.0,
    perUnit: PerUnit = PerUnit.GRAM,
    servingG: Double? = null,
    barcode: String? = null,
): CustomFood = CustomFood(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    nutrients = NutrientsPer100(kcal, protein = 13.5, carbs = 58.7, sugar = 1.1, fat = 7.0, fiber = 10.0, salt = 0.02),
    perUnit = perUnit,
    servingG = servingG,
    createdAtMillis = 1,
    updatedAtMillis = 1,
)

/** A food of the bundled database (BLS code or EAN as [ref]); [id] is its relevance rank within the source. */
fun testDbFood(
    source: FoodSource,
    ref: String,
    name: String,
    brand: String? = null,
    kcal: Double = 100.0,
    id: Long = 0,
    popularity: Int = 0,
    perUnit: PerUnit = PerUnit.GRAM,
    servingG: Double? = null,
): Food = Food(
    id = id,
    key = FoodKey(source, ref),
    barcode = ref.takeIf { source == FoodSource.OFF },
    name = name,
    brand = brand,
    nutrients = NutrientsPer100(kcal, protein = 10.0, carbs = 20.0, sugar = 5.0, fat = 4.0, fiber = 2.0, salt = 0.5),
    perUnit = perUnit,
    servingG = servingG,
    servingLabel = servingG?.let { "Portion" },
    popularity = popularity,
)

fun testBls(ref: String, name: String, kcal: Double = 100.0, id: Long = 0) =
    testDbFood(FoodSource.BLS, ref, name, kcal = kcal, id = id)

fun testOff(ean: String, name: String, brand: String? = null, kcal: Double = 100.0, popularity: Int = 0, id: Long = 0) =
    testDbFood(FoodSource.OFF, ean, name, brand, kcal, id, popularity)

/** The data the Heute screen shows for [date], built the same way the ViewModel does. */
fun dayData(
    date: LocalDate,
    entries: List<DiaryEntry> = emptyList(),
    previous: List<DiaryEntry> = emptyList(),
): de.privat.kalorienzaehler.ui.today.DayData = de.privat.kalorienzaehler.ui.today.DayData(
    date = date,
    sections = de.privat.kalorienzaehler.domain.diary.DiaryMath.sections(entries),
    totals = de.privat.kalorienzaehler.domain.diary.DiaryMath.totals(entries),
    previousDayCounts = previous.groupingBy { it.meal }.eachCount(),
)
