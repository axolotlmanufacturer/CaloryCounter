package de.privat.kalorienzaehler.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Maps [UserProfile] to and from DataStore preferences. A profile that cannot be read completely
 * (missing keys, unknown names) counts as "no profile", which sends the user back to onboarding
 * instead of crashing.
 */
object ProfilePreferences {

    /** Bump when the stored layout changes incompatibly. */
    const val SCHEMA_VERSION = 1

    private val VERSION = intPreferencesKey("profile_version")
    private val SEX = stringPreferencesKey("sex")
    private val BIRTH_DATE = stringPreferencesKey("birth_date")
    private val HEIGHT_CM = doublePreferencesKey("height_cm")
    private val ACTIVITY = stringPreferencesKey("activity_level")
    private val BODY_FAT = doublePreferencesKey("body_fat_percent")
    private val FORMULA = stringPreferencesKey("bmr_formula")
    private val GOAL_TYPE = stringPreferencesKey("goal_type")
    private val GOAL_RATE = doublePreferencesKey("goal_weekly_rate_kg")
    private val GOAL_WEIGHT = doublePreferencesKey("goal_weight_kg")
    private val MACRO_MODE = stringPreferencesKey("macro_mode")
    private val MACRO_A = doublePreferencesKey("macro_a")
    private val MACRO_B = doublePreferencesKey("macro_b")
    private val MACRO_C = doublePreferencesKey("macro_c")
    private val ENERGY_UNIT = stringPreferencesKey("energy_unit")

    private const val MODE_DEFAULT = "DEFAULT"
    private const val MODE_PERCENT = "PERCENT"
    private const val MODE_PER_KG = "PER_KG"

    fun read(preferences: Preferences): UserProfile? = try {
        readOrThrow(preferences)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeParseException) {
        null
    }

    private fun readOrThrow(p: Preferences): UserProfile? {
        if (p[VERSION] != SCHEMA_VERSION) return null
        return UserProfile(
            sex = Sex.valueOf(p[SEX] ?: return null),
            birthDate = LocalDate.parse(p[BIRTH_DATE] ?: return null),
            heightCm = p[HEIGHT_CM] ?: return null,
            activityLevel = ActivityLevel.valueOf(p[ACTIVITY] ?: return null),
            bodyFatPercent = p[BODY_FAT],
            formula = BmrFormula.valueOf(p[FORMULA] ?: return null),
            goal = Goal(
                type = GoalType.valueOf(p[GOAL_TYPE] ?: return null),
                weeklyRateKg = p[GOAL_RATE] ?: return null,
                weightKg = p[GOAL_WEIGHT],
            ),
            macros = readMacros(p) ?: return null,
            energyUnit = EnergyUnit.valueOf(p[ENERGY_UNIT] ?: return null),
        )
    }

    private fun readMacros(p: Preferences): MacroSettings? = when (p[MACRO_MODE]) {
        MODE_DEFAULT -> MacroSettings.Default
        MODE_PERCENT -> MacroSettings.PercentSplit(p[MACRO_A] ?: return null, p[MACRO_B] ?: return null, p[MACRO_C] ?: return null)
        MODE_PER_KG -> MacroSettings.PerKilogram(p[MACRO_A] ?: return null, p[MACRO_B] ?: return null)
        else -> null
    }

    fun write(preferences: MutablePreferences, profile: UserProfile) {
        preferences[VERSION] = SCHEMA_VERSION
        preferences[SEX] = profile.sex.name
        preferences[BIRTH_DATE] = profile.birthDate.toString()
        preferences[HEIGHT_CM] = profile.heightCm
        preferences[ACTIVITY] = profile.activityLevel.name
        preferences.setOrRemove(BODY_FAT, profile.bodyFatPercent)
        preferences[FORMULA] = profile.formula.name
        preferences[GOAL_TYPE] = profile.goal.type.name
        preferences[GOAL_RATE] = profile.goal.weeklyRateKg
        preferences.setOrRemove(GOAL_WEIGHT, profile.goal.weightKg)
        preferences[ENERGY_UNIT] = profile.energyUnit.name
        preferences.remove(MACRO_A)
        preferences.remove(MACRO_B)
        preferences.remove(MACRO_C)
        when (val macros = profile.macros) {
            MacroSettings.Default -> preferences[MACRO_MODE] = MODE_DEFAULT
            is MacroSettings.PercentSplit -> {
                preferences[MACRO_MODE] = MODE_PERCENT
                preferences[MACRO_A] = macros.proteinPercent
                preferences[MACRO_B] = macros.carbsPercent
                preferences[MACRO_C] = macros.fatPercent
            }
            is MacroSettings.PerKilogram -> {
                preferences[MACRO_MODE] = MODE_PER_KG
                preferences[MACRO_A] = macros.proteinGPerKg
                preferences[MACRO_B] = macros.fatGPerKg
            }
        }
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<Double>, value: Double?) {
        if (value == null) remove(key) else this[key] = value
    }
}
