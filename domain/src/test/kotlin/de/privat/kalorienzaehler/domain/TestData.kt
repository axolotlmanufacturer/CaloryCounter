package de.privat.kalorienzaehler.domain

import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import java.time.LocalDate

/** Fixed "today" for deterministic tests. */
val TODAY: LocalDate = LocalDate.of(2026, 10, 13)

/** Birth date that makes the person exactly [years] years old on [TODAY] (birthday earlier this year). */
fun birthDateForAge(years: Int): LocalDate = TODAY.minusYears(years.toLong()).minusMonths(1)

fun profile(
    sex: Sex = Sex.MALE,
    age: Int = 30,
    heightCm: Double = 180.0,
    activity: ActivityLevel = ActivityLevel.MODERATE,
    bodyFat: Double? = null,
    formula: BmrFormula = BmrFormula.MIFFLIN_ST_JEOR,
    goal: Goal = Goal.MAINTAIN,
    macros: MacroSettings = MacroSettings.Default,
): UserProfile = UserProfile(
    sex = sex,
    birthDate = birthDateForAge(age),
    heightCm = heightCm,
    activityLevel = activity,
    bodyFatPercent = bodyFat,
    formula = formula,
    goal = goal,
    macros = macros,
    energyUnit = EnergyUnit.KCAL,
)
