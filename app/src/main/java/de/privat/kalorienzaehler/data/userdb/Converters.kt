package de.privat.kalorienzaehler.data.userdb

import androidx.room.TypeConverter
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.PerUnit
import java.time.LocalDate

/** Dates are stored as ISO text (`2026-10-13`): readable, sortable, easy to export. */
class Converters {
    @TypeConverter fun dateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter fun textToDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    @TypeConverter fun mealToText(meal: Meal): String = meal.name

    @TypeConverter fun textToMeal(text: String): Meal = Meal.valueOf(text)

    @TypeConverter fun sourceToText(source: FoodSource): String = source.name

    @TypeConverter fun textToSource(text: String): FoodSource = FoodSource.valueOf(text)

    @TypeConverter fun unitToText(unit: PerUnit): String = unit.code

    @TypeConverter fun textToUnit(text: String): PerUnit = PerUnit.fromCode(text)
}
