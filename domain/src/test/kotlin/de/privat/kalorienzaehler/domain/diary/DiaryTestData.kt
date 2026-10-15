package de.privat.kalorienzaehler.domain.diary

import java.time.LocalDate

val DAY: LocalDate = LocalDate.of(2026, 10, 13)

/** A logged food: 150 g of something with 200 kcal / 10 g protein / 30 g carbs / 5 g fat per 100 g. */
fun entry(
    id: Long = 1,
    date: LocalDate = DAY,
    meal: Meal = Meal.FRUEHSTUECK,
    source: FoodSource = FoodSource.CUSTOM,
    name: String = "Haferbrei",
    amount: Double = 150.0,
    unit: String = "g",
    grams: Double = amount,
    nutrients: NutrientAmounts = NutrientsPer100(200.0, 10.0, 30.0, null, 5.0).forGrams(grams),
    createdAtMillis: Long = id,
): DiaryEntry = DiaryEntry(
    id = id,
    date = date,
    meal = meal,
    source = source,
    foodRef = if (source == FoodSource.QUICK) null else "7",
    name = name,
    brand = null,
    amount = amount,
    unit = unit,
    grams = grams,
    nutrients = nutrients,
    createdAtMillis = createdAtMillis,
)

fun quickEntry(
    id: Long = 1,
    meal: Meal = Meal.SNACKS,
    nutrients: NutrientAmounts = NutrientAmounts(100.0),
): DiaryEntry = entry(
    id = id,
    meal = meal,
    source = FoodSource.QUICK,
    name = "Schnelleintrag",
    amount = 1.0,
    unit = AmountUnit.PORTION,
    grams = 0.0,
    nutrients = nutrients,
)
