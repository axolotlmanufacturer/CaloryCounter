package de.privat.kalorienzaehler.domain.diary

import de.privat.kalorienzaehler.domain.format.DecimalInput

/** What is wrong with a field of the diary forms (quick entry, custom food, amount). */
enum class FormError {
    /** Nothing entered yet. */
    REQUIRED,

    /** Text that is not a number. */
    UNREADABLE,

    /** A number outside the plausible range (typo guard). */
    OUT_OF_RANGE,

    /** Text longer than allowed. */
    TOO_LONG,

    /** A barcode that is not 8 to 14 digits. */
    INVALID_BARCODE,
}

/** Typo guards for the diary forms. Hints about implausible values are [FoodWarning]s, not limits. */
object FormLimits {
    const val MAX_NAME_LENGTH = 100
    const val MAX_BRAND_LENGTH = 100
    val ENERGY_KCAL: ClosedFloatingPointRange<Double> = 0.0..9999.0
    val NUTRIENT_G: ClosedFloatingPointRange<Double> = 0.0..1000.0
    val SERVING_G: ClosedFloatingPointRange<Double> = 0.1..5000.0
    val AMOUNT: ClosedFloatingPointRange<Double> = 0.1..10000.0
    val BARCODE_LENGTH: IntRange = 8..14
}

internal class FieldReader<F : Any> {
    val errors = LinkedHashMap<F, FormError>()

    /** A number that must be present. */
    fun required(field: F, text: String, range: ClosedFloatingPointRange<Double>): Double? {
        if (text.isBlank()) {
            errors[field] = FormError.REQUIRED
            return null
        }
        return read(field, text, range)
    }

    /** A number that may be left empty (`null` then means "not known", never zero). */
    fun optional(field: F, text: String, range: ClosedFloatingPointRange<Double>): Double? =
        if (text.isBlank()) null else read(field, text, range)

    private fun read(field: F, text: String, range: ClosedFloatingPointRange<Double>): Double? {
        val value = DecimalInput.parse(text)
        if (value == null) {
            errors[field] = FormError.UNREADABLE
            return null
        }
        if (value !in range) {
            errors[field] = FormError.OUT_OF_RANGE
            return null
        }
        return value
    }
}

/** Reads the amount typed on the portion screen. */
object AmountInput {

    /** The amount, or why [text] is not usable (`REQUIRED` while empty). */
    fun parse(text: String): Result {
        if (text.isBlank()) return Result(null, FormError.REQUIRED)
        val value = DecimalInput.parse(text) ?: return Result(null, FormError.UNREADABLE)
        if (value !in FormLimits.AMOUNT) return Result(null, FormError.OUT_OF_RANGE)
        return Result(value, null)
    }

    data class Result(val amount: Double?, val error: FormError?)
}
