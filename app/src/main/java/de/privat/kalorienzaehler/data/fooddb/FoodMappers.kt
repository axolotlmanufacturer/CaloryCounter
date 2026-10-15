package de.privat.kalorienzaehler.data.fooddb

import de.privat.kalorienzaehler.domain.diary.Food
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodPortion
import de.privat.kalorienzaehler.domain.diary.NutrientsPer100

fun FoodEntity.toDomain(): Food = Food(
    id = id,
    key = FoodKey(source, sourceId),
    barcode = barcode,
    name = name,
    brand = brand,
    nutrients = NutrientsPer100(kcal, protein, carbs, sugar, fat, fiber, salt),
    perUnit = perUnit,
    servingG = servingG,
    servingLabel = servingLabel,
    popularity = popularity,
)

fun FoodPortionEntity.toDomain(): FoodPortion = FoodPortion(label, grams, estimated)
