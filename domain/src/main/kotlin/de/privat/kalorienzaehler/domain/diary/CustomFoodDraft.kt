package de.privat.kalorienzaehler.domain.diary

import de.privat.kalorienzaehler.domain.format.GermanFormat

enum class CustomFoodField { NAME, BRAND, BARCODE, KCAL, PROTEIN, CARBS, SUGAR, FAT, FIBER, SALT, SERVING }

/** A valid custom food form, ready to be stored. */
data class CustomFoodInput(
    val barcode: String?,
    val name: String,
    val brand: String?,
    val nutrients: NutrientsPer100,
    val perUnit: PerUnit,
    val servingG: Double?,
)

data class CustomFoodEvaluation(
    val errors: Map<CustomFoodField, FormError>,
    /** The finished food; `null` while there are errors. */
    val input: CustomFoodInput?,
    /** Hints about values that do not add up; shown while typing, never blocking. */
    val warnings: List<FoodWarning>,
) {
    val isValid: Boolean get() = errors.isEmpty() && input != null
}

/** Number text for form fields: German decimal comma, no thousands separator (it must parse back). */
internal object FormText {
    fun number(value: Double): String = GermanFormat.editable(value, maxFractionDigits = 2)
}

/**
 * Form state for "Eigenes Lebensmittel": values per 100 g or 100 ml as printed on the pack, plus an
 * optional portion size. Everything is kept as text; optional values left empty stay unknown.
 */
data class CustomFoodDraft(
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val perUnit: PerUnit = PerUnit.GRAM,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val sugar: String = "",
    val fat: String = "",
    val fiber: String = "",
    val salt: String = "",
    val servingG: String = "",
) {
    fun evaluate(): CustomFoodEvaluation {
        val reader = FieldReader<CustomFoodField>()

        val trimmedName = name.trim()
        when {
            trimmedName.isEmpty() -> reader.errors[CustomFoodField.NAME] = FormError.REQUIRED
            trimmedName.length > FormLimits.MAX_NAME_LENGTH -> reader.errors[CustomFoodField.NAME] = FormError.TOO_LONG
        }
        val trimmedBrand = brand.trim()
        if (trimmedBrand.length > FormLimits.MAX_BRAND_LENGTH) reader.errors[CustomFoodField.BRAND] = FormError.TOO_LONG
        val trimmedBarcode = barcode.trim()
        if (trimmedBarcode.isNotEmpty() &&
            (trimmedBarcode.length !in FormLimits.BARCODE_LENGTH || !trimmedBarcode.all { it in '0'..'9' })
        ) {
            reader.errors[CustomFoodField.BARCODE] = FormError.INVALID_BARCODE
        }

        val kcalValue = reader.required(CustomFoodField.KCAL, kcal, FormLimits.ENERGY_KCAL)
        val proteinValue = reader.optional(CustomFoodField.PROTEIN, protein, FormLimits.NUTRIENT_G)
        val carbsValue = reader.optional(CustomFoodField.CARBS, carbs, FormLimits.NUTRIENT_G)
        val sugarValue = reader.optional(CustomFoodField.SUGAR, sugar, FormLimits.NUTRIENT_G)
        val fatValue = reader.optional(CustomFoodField.FAT, fat, FormLimits.NUTRIENT_G)
        val fiberValue = reader.optional(CustomFoodField.FIBER, fiber, FormLimits.NUTRIENT_G)
        val saltValue = reader.optional(CustomFoodField.SALT, salt, FormLimits.NUTRIENT_G)
        val servingValue = reader.optional(CustomFoodField.SERVING, servingG, FormLimits.SERVING_G)

        val nutrients = kcalValue?.let {
            NutrientsPer100(it, proteinValue, carbsValue, sugarValue, fatValue, fiberValue, saltValue)
        }
        val input = if (reader.errors.isEmpty() && nutrients != null) {
            CustomFoodInput(
                barcode = trimmedBarcode.ifEmpty { null },
                name = trimmedName,
                brand = trimmedBrand.ifEmpty { null },
                nutrients = nutrients,
                perUnit = perUnit,
                servingG = servingValue,
            )
        } else {
            null
        }
        return CustomFoodEvaluation(
            errors = reader.errors,
            input = input,
            warnings = nutrients?.let(FoodPlausibility::check).orEmpty(),
        )
    }

    companion object {
        /** The form for changing an existing custom food. */
        fun from(food: CustomFood): CustomFoodDraft = CustomFoodDraft(
            name = food.name,
            brand = food.brand.orEmpty(),
            barcode = food.barcode.orEmpty(),
            perUnit = food.perUnit,
            kcal = FormText.number(food.nutrients.kcal),
            protein = food.nutrients.protein?.let(FormText::number).orEmpty(),
            carbs = food.nutrients.carbs?.let(FormText::number).orEmpty(),
            sugar = food.nutrients.sugar?.let(FormText::number).orEmpty(),
            fat = food.nutrients.fat?.let(FormText::number).orEmpty(),
            fiber = food.nutrients.fiber?.let(FormText::number).orEmpty(),
            salt = food.nutrients.salt?.let(FormText::number).orEmpty(),
            servingG = food.servingG?.let(FormText::number).orEmpty(),
        )
    }
}
