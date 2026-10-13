package de.privat.kalorienzaehler.ui.diary

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.FoodWarning
import de.privat.kalorienzaehler.domain.diary.FormError
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.domain.energy.Energy
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.format.GermanFormat
import java.time.LocalDate

// German wording for the diary: meals, dates, amounts, form errors and plausibility hints.

@get:StringRes
val Meal.labelRes: Int
    get() = when (this) {
        Meal.FRUEHSTUECK -> R.string.meal_breakfast
        Meal.MITTAGESSEN -> R.string.meal_lunch
        Meal.ABENDESSEN -> R.string.meal_dinner
        Meal.SNACKS -> R.string.meal_snacks
    }

@Composable
fun Meal.label(): String = stringResource(labelRes)

/** "Heute", "Gestern", "Morgen", otherwise "Mo., 12.10.2026". */
@Composable
fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> stringResource(R.string.date_weekday_value, GermanFormat.weekdayShort(date), GermanFormat.date(date))
}

/** The energy as a bare number in the chosen unit: `2.209` (kcal) or `9.243` (kJ). */
fun energyNumber(kcal: Double, unit: EnergyUnit): String = when (unit) {
    EnergyUnit.KCAL -> GermanFormat.wholeNumber(kcal)
    EnergyUnit.KJ -> GermanFormat.wholeNumber(Energy.kcalToKj(kcal))
}

@Composable
fun energyUnitLabel(unit: EnergyUnit): String = when (unit) {
    EnergyUnit.KCAL -> stringResource(R.string.unit_kcal)
    EnergyUnit.KJ -> stringResource(R.string.unit_kj)
}

/** `150 g`, `250 ml`; portion units already carry their size and read `2 × Portion (30 g)`. */
@Composable
fun amountText(amount: Double, unitKey: String): String {
    val number = GermanFormat.upTo(amount, 2)
    return when (unitKey) {
        PerUnit.GRAM.code -> stringResource(R.string.unit_g_value, number)
        PerUnit.MILLILITER.code -> stringResource(R.string.unit_ml_value, number)
        else -> stringResource(R.string.entry_amount_labeled, number, unitKey)
    }
}

/** What an entry shows under its name: the amount, `null` for quick entries (they have none). */
@Composable
fun DiaryEntry.amountDescription(): String? = if (isQuick) null else amountText(amount, unit)

@Composable
fun DiaryEntry.displayName(): String =
    name.ifBlank { stringResource(R.string.entry_quick_default_label) }

/** Brand and amount in one line, whatever of the two exists. */
@Composable
fun DiaryEntry.detailLine(): String? {
    val brand = brand?.takeIf { it.isNotBlank() }
    val amount = amountDescription()
    return when {
        brand != null && amount != null -> stringResource(R.string.entry_details, brand, amount)
        else -> brand ?: amount
    }
}

@Composable
fun FoodWarning.messageText(): String = when (this) {
    is FoodWarning.KcalMacroMismatch ->
        stringResource(R.string.warning_kcal_mismatch, GermanFormat.wholeNumber(expectedKcal))
    FoodWarning.EnergyAboveMaximum -> stringResource(R.string.warning_energy_above_max)
    FoodWarning.NutrientsExceed100g -> stringResource(R.string.warning_nutrients_exceed)
    FoodWarning.SugarExceedsCarbs -> stringResource(R.string.warning_sugar_exceeds_carbs)
}

/** The message under a field, or `null` for an empty required field (the button just stays off). */
@Composable
fun FormError.messageText(): String? = when (this) {
    FormError.REQUIRED -> null
    FormError.UNREADABLE -> stringResource(R.string.error_number)
    FormError.OUT_OF_RANGE -> stringResource(R.string.error_number_range)
    FormError.TOO_LONG -> stringResource(R.string.error_too_long)
    FormError.INVALID_BARCODE -> stringResource(R.string.error_barcode)
}
