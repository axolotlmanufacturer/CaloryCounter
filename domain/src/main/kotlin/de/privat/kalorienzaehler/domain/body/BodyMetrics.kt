package de.privat.kalorienzaehler.domain.body

import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.Sex
import java.time.LocalDate
import java.time.Period
import kotlin.math.ceil

/** Age, BMI and the weight that corresponds to a BMI. */
object BodyMetrics {

    /** Lowest BMI that counts as normal weight; below it "Abnehmen" is not offered (§6.4). */
    const val MIN_NORMAL_BMI: Double = 18.5

    /** Whole years completed on [today]. Negative if [birthDate] lies in the future. */
    fun ageYears(birthDate: LocalDate, today: LocalDate): Int =
        if (birthDate.isAfter(today)) -1 else Period.between(birthDate, today).years

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    /**
     * Smallest weight (rounded up to 0,1 kg, so the displayed value is itself valid) whose BMI is
     * at least [MIN_NORMAL_BMI].
     */
    fun minWeightForNormalBmi(heightCm: Double): Double {
        val heightM = heightCm / 100.0
        return ceil(MIN_NORMAL_BMI * heightM * heightM * 10.0 - 1e-9) / 10.0
    }
}

/** Basal metabolic rate (Grundumsatz), PROMPT.md §6.1. */
object Bmr {

    /** Mifflin-St Jeor: `10 × kg + 6,25 × cm − 5 × age + 5` (male) or `− 161` (female). */
    fun mifflinStJeor(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    /** Katch-McArdle: `370 + 21,6 × lean body mass`. */
    fun katchMcArdle(weightKg: Double, bodyFatPercent: Double): Double {
        val leanBodyMassKg = weightKg * (1.0 - bodyFatPercent / 100.0)
        return 370.0 + 21.6 * leanBodyMassKg
    }
}

/** Total daily energy expenditure (Gesamtumsatz), PROMPT.md §6.2. */
object Tdee {
    fun of(bmrKcal: Double, activityLevel: ActivityLevel): Double = bmrKcal * activityLevel.factor
}
