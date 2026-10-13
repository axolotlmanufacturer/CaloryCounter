package de.privat.kalorienzaehler.data.userdb.entity

/** The four meals of the diary (PROMPT.md §5.3); stored by name. */
enum class Meal { FRUEHSTUECK, MITTAGESSEN, ABENDESSEN, SNACKS }

/** Where a diary entry's food came from; stored by name. */
enum class FoodSource { BLS, OFF, CUSTOM, QUICK }

/** Reference unit of nutrition values: per 100 g or per 100 ml (1 ml counts as 1 g). */
enum class PerUnit(val code: String) {
    GRAM("g"),
    MILLILITER("ml"),
    ;

    companion object {
        fun fromCode(code: String): PerUnit = entries.first { it.code == code }
    }
}
