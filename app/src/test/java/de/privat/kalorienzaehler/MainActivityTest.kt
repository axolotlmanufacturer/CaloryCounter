package de.privat.kalorienzaehler

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.ui.settings.PROFILE_TARGET_TAG
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The real activity with its Hilt graph, but in-memory repositories and a fixed date. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "+w411dp-h891dp-xxhdpi")
class MainActivityTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var profiles: FakeProfileRepository

    @Inject lateinit var weights: FakeWeightRepository

    @Before
    fun inject() = hilt.inject()

    private fun tab(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    @Test
    fun firstLaunchStartsWithOnboarding() {
        compose.onNodeWithText(string(R.string.onboarding_welcome_title)).assertIsDisplayed()
    }

    @Test
    fun withASavedProfileTheAppOpensOnHeute() {
        profiles.preset(sampleProfile())
        weights.preset(WeightPoint(TEST_TODAY, 80.0))

        tab(R.string.nav_today).assertIsSelected()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertIsDisplayed()
    }

    @Test
    fun finishingOnboardingLeadsStraightIntoTheApp() {
        compose.onNodeWithText(string(R.string.onboarding_welcome_start)).performClick()
        compose.onNodeWithText(string(R.string.sex_female)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.field_birth_date)).performScrollTo().performTextInput("13.09.1996")
        compose.onNodeWithText(string(R.string.action_next)).performClick()
        compose.onNodeWithText(string(R.string.field_height)).performScrollTo().performTextInput("165")
        compose.onNodeWithText(string(R.string.field_weight)).performScrollTo().performTextInput("65")
        compose.onNodeWithText(string(R.string.action_next)).performClick()
        compose.onNodeWithText(string(R.string.activity_light)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.action_next)).performClick()
        compose.onNodeWithText(string(R.string.action_next)).performClick() // goal: Halten
        compose.onNodeWithText(string(R.string.action_done)).performClick()

        tab(R.string.nav_today).assertIsSelected()
        assertNotNull(profiles.stored)
        assertEquals(listOf(WeightPoint(TEST_TODAY, 65.0)), weights.stored)
    }

    @Test
    fun theSavedProfileShowsUpInTheProfilTab() {
        profiles.preset(sampleProfile())
        weights.preset(WeightPoint(TEST_TODAY, 80.0))

        tab(R.string.nav_profile).performClick()
        compose.onNodeWithTag(PROFILE_TARGET_TAG).assertTextEquals(string(R.string.unit_kcal_value, "2.209"))
    }
}
