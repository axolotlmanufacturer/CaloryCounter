package de.privat.kalorienzaehler.domain.nutrition

import kotlin.math.abs

/** Energy per gram of each macronutrient (PROMPT.md §6.5). */
object MacroEnergy {
    const val PROTEIN_KCAL_PER_G: Double = 4.0
    const val CARBS_KCAL_PER_G: Double = 4.0
    const val FAT_KCAL_PER_G: Double = 9.0
}

/** How the daily macro targets are derived from the calorie target. */
sealed interface MacroSettings {

    /** Eiweiß 1,6 g/kg, Fett 30 % der Kalorien (20 % wenn sonst kein Platz bleibt), Rest Kohlenhydrate. */
    data object Default : MacroSettings

    /** Custom split; the three percentages must add up to 100. */
    data class PercentSplit(
        val proteinPercent: Double,
        val carbsPercent: Double,
        val fatPercent: Double,
    ) : MacroSettings {
        val isValid: Boolean
            get() = proteinPercent >= 0 && carbsPercent >= 0 && fatPercent >= 0 &&
                abs(proteinPercent + carbsPercent + fatPercent - 100.0) < 0.5
    }

    /** Eiweiß und Fett in g pro kg Körpergewicht; Kohlenhydrate füllen den Rest. */
    data class PerKilogram(
        val proteinGPerKg: Double,
        val fatGPerKg: Double,
    ) : MacroSettings {
        val isValid: Boolean get() = proteinGPerKg >= 0 && fatGPerKg >= 0
    }
}

/** Daily macro targets in grams. */
data class MacroTargets(
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    /** Eiweiß musste unter 1,6 g/kg gesenkt werden, weil das Kalorienziel sonst nicht reicht. */
    val proteinReduced: Boolean = false,
    /** Die gewählten g/kg-Werte brauchen mehr Kalorien als das Ziel; Kohlenhydrate sind 0. */
    val exceedsCalorieTarget: Boolean = false,
) {
    val kcal: Double
        get() = proteinG * MacroEnergy.PROTEIN_KCAL_PER_G +
            carbsG * MacroEnergy.CARBS_KCAL_PER_G +
            fatG * MacroEnergy.FAT_KCAL_PER_G
}

object MacroCalculator {

    const val DEFAULT_PROTEIN_G_PER_KG: Double = 1.6
    const val DEFAULT_FAT_SHARE: Double = 0.30
    const val REDUCED_FAT_SHARE: Double = 0.20

    fun targets(settings: MacroSettings, targetKcal: Double, weightKg: Double): MacroTargets = when (settings) {
        MacroSettings.Default -> default(targetKcal, weightKg)
        is MacroSettings.PercentSplit -> percentSplit(settings, targetKcal)
        is MacroSettings.PerKilogram -> perKilogram(settings, targetKcal, weightKg)
    }

    /**
     * Protein 1,6 g/kg, fat 30 % of the calories, carbs the remainder. If the remainder would be
     * negative, fat drops to 20 % first. If even that is not enough (very heavy person at a low
     * target), carbs are 0 and protein is lowered so that protein and fat fit the target.
     */
    private fun default(targetKcal: Double, weightKg: Double): MacroTargets {
        val wantedProteinG = DEFAULT_PROTEIN_G_PER_KG * weightKg
        val proteinKcal = wantedProteinG * MacroEnergy.PROTEIN_KCAL_PER_G

        var fatKcal = DEFAULT_FAT_SHARE * targetKcal
        var carbsKcal = targetKcal - proteinKcal - fatKcal
        if (carbsKcal < 0.0) {
            fatKcal = REDUCED_FAT_SHARE * targetKcal
            carbsKcal = targetKcal - proteinKcal - fatKcal
        }
        if (carbsKcal >= 0.0) {
            return MacroTargets(
                proteinG = wantedProteinG,
                carbsG = carbsKcal / MacroEnergy.CARBS_KCAL_PER_G,
                fatG = fatKcal / MacroEnergy.FAT_KCAL_PER_G,
            )
        }
        return MacroTargets(
            proteinG = (targetKcal - fatKcal) / MacroEnergy.PROTEIN_KCAL_PER_G,
            carbsG = 0.0,
            fatG = fatKcal / MacroEnergy.FAT_KCAL_PER_G,
            proteinReduced = true,
        )
    }

    private fun percentSplit(settings: MacroSettings.PercentSplit, targetKcal: Double) = MacroTargets(
        proteinG = targetKcal * settings.proteinPercent / 100.0 / MacroEnergy.PROTEIN_KCAL_PER_G,
        carbsG = targetKcal * settings.carbsPercent / 100.0 / MacroEnergy.CARBS_KCAL_PER_G,
        fatG = targetKcal * settings.fatPercent / 100.0 / MacroEnergy.FAT_KCAL_PER_G,
    )

    private fun perKilogram(settings: MacroSettings.PerKilogram, targetKcal: Double, weightKg: Double): MacroTargets {
        val proteinG = settings.proteinGPerKg * weightKg
        val fatG = settings.fatGPerKg * weightKg
        val remainingKcal = targetKcal -
            proteinG * MacroEnergy.PROTEIN_KCAL_PER_G -
            fatG * MacroEnergy.FAT_KCAL_PER_G
        return MacroTargets(
            proteinG = proteinG,
            carbsG = maxOf(0.0, remainingKcal) / MacroEnergy.CARBS_KCAL_PER_G,
            fatG = fatG,
            exceedsCalorieTarget = remainingKcal < 0.0,
        )
    }
}
