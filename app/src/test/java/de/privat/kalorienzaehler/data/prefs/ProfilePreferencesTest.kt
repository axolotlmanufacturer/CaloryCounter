package de.privat.kalorienzaehler.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.nutrition.MacroSettings
import de.privat.kalorienzaehler.testing.sampleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePreferencesTest {

    private fun roundTrip(profile: UserProfile): UserProfile? {
        val prefs: MutablePreferences = mutablePreferencesOf()
        ProfilePreferences.write(prefs, profile)
        return ProfilePreferences.read(prefs)
    }

    @Test
    fun emptyPreferencesMeanNoProfile() {
        assertNull(ProfilePreferences.read(emptyPreferences()))
    }

    @Test
    fun aProfileSurvivesTheRoundTrip() {
        val profile = sampleProfile()
        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun optionalValuesAreKeptOrAbsent() {
        val full = sampleProfile(bodyFatPercent = 18.5, formula = BmrFormula.KATCH_MCARDLE, energyUnit = EnergyUnit.KJ)
        assertEquals(full, roundTrip(full))
        val none = sampleProfile(goal = Goal.MAINTAIN)
        assertEquals(none, roundTrip(none))
        assertNull(roundTrip(none)?.bodyFatPercent)
        assertNull(roundTrip(none)?.goal?.weightKg)
    }

    @Test
    fun everyMacroSettingRoundTrips() {
        listOf(
            MacroSettings.Default,
            MacroSettings.PercentSplit(30.0, 45.0, 25.0),
            MacroSettings.PerKilogram(2.0, 0.9),
        ).forEach { macros ->
            assertEquals(macros, roundTrip(sampleProfile(macros = macros)).also { requireNotNull(it) }?.macros)
        }
    }

    @Test
    fun switchingMacroModeLeavesNoStaleValues() {
        val prefs: MutablePreferences = mutablePreferencesOf()
        ProfilePreferences.write(prefs, sampleProfile(macros = MacroSettings.PercentSplit(30.0, 45.0, 25.0)))
        ProfilePreferences.write(prefs, sampleProfile(macros = MacroSettings.Default))
        assertEquals(MacroSettings.Default, ProfilePreferences.read(prefs)?.macros)
        assertEquals(
            "only version, profile fields and the macro mode remain",
            false,
            prefs.asMap().keys.any { it.name.startsWith("macro_") && it.name != "macro_mode" },
        )
    }

    @Test
    fun removingTheGoalWeightRemovesTheStoredValue() {
        val prefs: MutablePreferences = mutablePreferencesOf()
        ProfilePreferences.write(prefs, sampleProfile())
        ProfilePreferences.write(prefs, sampleProfile(goal = Goal.MAINTAIN))
        assertNull(ProfilePreferences.read(prefs)?.goal?.weightKg)
    }

    @Test
    fun unreadableStoredValuesMeanNoProfileInsteadOfACrash() {
        fun corrupted(change: (MutablePreferences) -> Unit): UserProfile? {
            val prefs: MutablePreferences = mutablePreferencesOf()
            ProfilePreferences.write(prefs, sampleProfile())
            change(prefs)
            return ProfilePreferences.read(prefs)
        }
        assertNull("unknown sex", corrupted { it[stringPreferencesKey("sex")] = "ROBOT" })
        assertNull("broken date", corrupted { it[stringPreferencesKey("birth_date")] = "13.10.1990" })
        assertNull("missing activity", corrupted { it.remove(stringPreferencesKey("activity_level")) })
        assertNull("unknown macro mode", corrupted { it[stringPreferencesKey("macro_mode")] = "???" })
        assertNull("other layout version", corrupted { it[androidx.datastore.preferences.core.intPreferencesKey("profile_version")] = 99 })
    }
}
