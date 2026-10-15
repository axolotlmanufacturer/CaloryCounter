package de.privat.kalorienzaehler.testing

import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import java.time.LocalDate

/** The first PROMPT.md §6.8 vector (male, 30 y, 180 cm, MODERATE; 80 kg) as a stored profile. */
fun sampleProfile(
    goal: Goal = Goal(GoalType.LOSE, weeklyRateKg = 0.5, weightKg = 72.0),
    macros: MacroSettings = MacroSettings.Default,
    bodyFatPercent: Double? = null,
    formula: BmrFormula = BmrFormula.MIFFLIN_ST_JEOR,
    energyUnit: EnergyUnit = EnergyUnit.KCAL,
): UserProfile = UserProfile(
    sex = Sex.MALE,
    birthDate = LocalDate.of(1996, 9, 13),
    heightCm = 180.0,
    activityLevel = ActivityLevel.MODERATE,
    bodyFatPercent = bodyFatPercent,
    formula = formula,
    goal = goal,
    macros = macros,
    energyUnit = energyUnit,
)
