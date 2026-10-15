package de.privat.kalorienzaehler.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+w411dp-h891dp-xxhdpi")
class ProfileScreenTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }
    private val weights = FakeWeightRepository().apply { preset(WeightPoint(TEST_TODAY.minusDays(3), 80.0)) }

    private fun show() {
        val viewModel = ProfileViewModel(profiles, weights, TEST_CLOCK)
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) { ProfileScreen(viewModel = viewModel) }
        }
    }

    private fun replace(label: String, text: String) =
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)

    private fun kcal(value: String) = string(R.string.unit_kcal_value, value)

    /** The preview card is at the top and scrolls away while a field further down is edited. */
    private fun assertTarget(text: String) =
        compose.onNodeWithTag(PROFILE_TARGET_TAG).assertTextEquals(text)

    @Test
    fun showsTheSavedValuesAndTheNumbersCalculatedFromThem() {
        show()
        compose.onNodeWithText("180").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("13.09.1996").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(kcal("1.780")).assertIsDisplayed()
        compose.onNodeWithText(kcal("2.759")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()
    }

    @Test
    fun theTargetFollowsEveryEditBeforeAnythingIsSaved() {
        show()
        replace(string(R.string.field_weight), "90")
        // BMR 1880 × 1,55 = 2914; minus 0,5 kg/week (550 kcal) = 2364
        assertTarget(kcal("2.364"))
        compose.onNodeWithText(kcal("1.880")).assertExists()
        compose.onNodeWithText(kcal("2.914")).assertExists()
        compose.onNodeWithText(string(R.string.action_save)).assertIsEnabled()
        assertEquals("nothing stored yet", listOf(WeightPoint(TEST_TODAY.minusDays(3), 80.0)), weights.stored)
    }

    @Test
    fun savingStoresTheProfileAndTheNewWeightAndConfirms() {
        show()
        replace(string(R.string.field_weight), "78,5")
        compose.onNodeWithText(string(R.string.action_save)).performClick()

        compose.onNodeWithText(string(R.string.saved_message)).assertIsDisplayed()
        assertEquals(WeightPoint(TEST_TODAY, 78.5), weights.stored.last())
        compose.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()
    }

    @Test
    fun anImplausibleValueIsExplainedAndCannotBeSaved() {
        show()
        replace(string(R.string.field_height), "18")
        compose.onNodeWithText(string(R.string.error_height_range)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()
    }

    @Test
    fun switchingToKilojouleShowsAllEnergyInKilojoule() {
        show()
        compose.onNodeWithText(string(R.string.energy_unit_kj)).performScrollTo().performClick()
        // 2209 kcal × 4,184 = 9242,4 kJ
        assertTarget(string(R.string.unit_kj_value, "9.242"))
        compose.onNodeWithText(string(R.string.action_save)).performClick()
        assertEquals(EnergyUnit.KJ, profiles.stored?.energyUnit)
    }

    @Test
    fun theFemaleVectorExplainsTheFloorAndTheRealisticRate() {
        profiles.preset(
            sampleProfile(goal = Goal(GoalType.LOSE, 0.75, 55.0)).copy(
                sex = Sex.FEMALE,
                heightCm = 165.0,
                activityLevel = ActivityLevel.LIGHT,
            ),
        )
        weights.preset(WeightPoint(TEST_TODAY, 65.0))
        show()

        compose.onNodeWithText(kcal("1.370")).assertIsDisplayed() // 1370,25
        compose.onNodeWithText(kcal("1.884")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.notice_rate_capped, "0,75", "0,65")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.notice_floor_applied, "0,62", "1.200")).assertIsDisplayed()
    }

    @Test
    fun overTheCalorieTargetIsNotThisScreensBusinessButTheNoticesAreNeutral() {
        // The notice cards use the secondary container, not the error colors (PROMPT.md §6.4 rule 5).
        profiles.preset(sampleProfile(goal = Goal(GoalType.LOSE, 1.0, 60.0)))
        weights.preset(WeightPoint(TEST_TODAY, 58.0)) // BMI 17,9: Abnehmen not offered
        show()
        compose.onNodeWithText(string(R.string.notice_lose_not_offered, "17,9")).assertIsDisplayed()
    }

    @Test
    fun minorsSeeWhyThereIsNoWeightGoal() {
        profiles.preset(sampleProfile(goal = Goal.MAINTAIN).copy(birthDate = LocalDate.of(2010, 9, 13)))
        show()
        compose.onNodeWithText(string(R.string.goal_not_allowed_hint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.goal_lose)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun katchMcArdleNeedsBodyFatBeforeItCanBeSaved() {
        show()
        compose.onNodeWithText(string(R.string.formula_katch)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.error_katch_needs_body_fat)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()

        replace(string(R.string.field_body_fat), "20")
        compose.onNodeWithText(kcal("1.752")).assertExists() // 370 + 21,6 × 64 = 1752,4
        compose.onNodeWithText(string(R.string.action_save)).assertIsEnabled()
    }

    @Test
    fun customPercentMacrosMustAddUpToOneHundred() {
        show()
        compose.onNodeWithText(string(R.string.macro_mode_percent)).performScrollTo().performClick()
        replace(string(R.string.macro_fat_percent), "40")
        compose.onNodeWithText(string(R.string.error_percents_sum)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()
        replace(string(R.string.macro_fat_percent), "30")
        compose.onNodeWithText(string(R.string.action_save)).assertIsEnabled()
    }
}
