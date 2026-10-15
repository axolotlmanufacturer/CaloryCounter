package de.privat.kalorienzaehler.domain.energy

/** Energy unit conversion. All energy values are computed in kcal as `Double`. */
object Energy {

    /** 1 kcal = 4.184 kJ (thermochemical calorie). */
    const val KJ_PER_KCAL: Double = 4.184

    fun kcalToKj(kcal: Double): Double = kcal * KJ_PER_KCAL

    fun kjToKcal(kj: Double): Double = kj / KJ_PER_KCAL
}

/** The unit in which energy is shown to the user (a setting; computation always uses kcal). */
enum class EnergyUnit { KCAL, KJ }
