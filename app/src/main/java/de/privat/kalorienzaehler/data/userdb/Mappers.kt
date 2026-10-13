package de.privat.kalorienzaehler.data.userdb

import de.privat.kalorienzaehler.data.userdb.entity.CustomFoodEntity
import de.privat.kalorienzaehler.data.userdb.entity.DiaryEntryEntity
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.diary.NutrientsPer100

fun DiaryEntryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    date = date,
    meal = meal,
    source = foodSource,
    foodRef = foodRef,
    name = nameSnapshot,
    brand = brandSnapshot,
    amount = amount,
    unit = unit,
    grams = grams,
    nutrients = NutrientAmounts(kcal, protein, carbs, fat),
    createdAtMillis = createdAt,
)

fun DiaryEntry.toEntity(): DiaryEntryEntity = DiaryEntryEntity(
    id = id,
    date = date,
    meal = meal,
    foodSource = source,
    foodRef = foodRef,
    nameSnapshot = name,
    brandSnapshot = brand,
    amount = amount,
    unit = unit,
    grams = grams,
    kcal = nutrients.kcal,
    protein = nutrients.protein,
    carbs = nutrients.carbs,
    fat = nutrients.fat,
    createdAt = createdAtMillis,
)

fun CustomFoodEntity.toDomain(): CustomFood = CustomFood(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    nutrients = NutrientsPer100(kcal, protein, carbs, sugar, fat, fiber, salt),
    perUnit = perUnit,
    servingG = servingG,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
)

fun CustomFood.toEntity(): CustomFoodEntity = CustomFoodEntity(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    kcal = nutrients.kcal,
    protein = nutrients.protein,
    carbs = nutrients.carbs,
    sugar = nutrients.sugar,
    fat = nutrients.fat,
    fiber = nutrients.fiber,
    salt = nutrients.salt,
    perUnit = perUnit,
    servingG = servingG,
    createdAt = createdAtMillis,
    updatedAt = updatedAtMillis,
)
