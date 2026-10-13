package de.privat.kalorienzaehler.domain.diary

enum class QuickEntryField { LABEL, KCAL, PROTEIN, CARBS, FAT }

/** What [QuickEntryDraft.evaluate] found out. */
data class QuickEntryEvaluation(
    val errors: Map<QuickEntryField, FormError>,
    /** The label as typed (trimmed); empty means "use the default label". */
    val label: String,
    /** The values to log; `null` while there are errors. */
    val nutrients: NutrientAmounts?,
    val warnings: List<FoodWarning>,
) {
    val isValid: Boolean get() = errors.isEmpty() && nutrients != null
}

/**
 * Form state of the "Schnelleintrag": calories plus optional macros and a label. Everything is kept
 * as text; macros left empty stay unknown (`null`), they are never turned into 0.
 */
data class QuickEntryDraft(
    val label: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    fun evaluate(): QuickEntryEvaluation {
        val reader = FieldReader<QuickEntryField>()
        val trimmedLabel = label.trim()
        if (trimmedLabel.length > FormLimits.MAX_NAME_LENGTH) reader.errors[QuickEntryField.LABEL] = FormError.TOO_LONG

        val kcalValue = reader.required(QuickEntryField.KCAL, kcal, FormLimits.ENERGY_KCAL)
        val proteinValue = reader.optional(QuickEntryField.PROTEIN, protein, FormLimits.NUTRIENT_G)
        val carbsValue = reader.optional(QuickEntryField.CARBS, carbs, FormLimits.NUTRIENT_G)
        val fatValue = reader.optional(QuickEntryField.FAT, fat, FormLimits.NUTRIENT_G)

        val nutrients = kcalValue?.let { NutrientAmounts(it, proteinValue, carbsValue, fatValue) }
        return QuickEntryEvaluation(
            errors = reader.errors,
            label = trimmedLabel,
            nutrients = nutrients.takeIf { reader.errors.isEmpty() },
            warnings = nutrients?.let(FoodPlausibility::checkAmounts).orEmpty(),
        )
    }

    companion object {
        /** The form for changing an existing quick entry. */
        fun from(entry: DiaryEntry): QuickEntryDraft = QuickEntryDraft(
            label = entry.name,
            kcal = FormText.number(entry.nutrients.kcal),
            protein = entry.nutrients.protein?.let(FormText::number).orEmpty(),
            carbs = entry.nutrients.carbs?.let(FormText::number).orEmpty(),
            fat = entry.nutrients.fat?.let(FormText::number).orEmpty(),
        )
    }
}
