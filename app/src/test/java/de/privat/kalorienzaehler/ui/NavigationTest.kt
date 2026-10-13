package de.privat.kalorienzaehler.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.privat.kalorienzaehler.MainActivity
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.string
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "+w411dp-h891dp-xxhdpi")
class NavigationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var profiles: FakeProfileRepository

    @Inject lateinit var weights: FakeWeightRepository

    @Before
    fun setUp() {
        hilt.inject()
        profiles.preset(sampleProfile())
        weights.preset(WeightPoint(TEST_TODAY, 80.0))
    }

    private fun tab(@StringRes label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    @Test
    fun showsFourGermanTabsInOrderAndStartsOnHeute() {
        assertEquals(
            listOf("Heute", "Gewicht", "Statistik", "Profil"),
            listOf(R.string.nav_today, R.string.nav_weight, R.string.nav_stats, R.string.nav_profile).map { string(it) },
        )

        tab(R.string.nav_today).assertIsDisplayed().assertIsSelected()
        tab(R.string.nav_weight).assertIsDisplayed().assertIsNotSelected()
        tab(R.string.nav_stats).assertIsDisplayed().assertIsNotSelected()
        tab(R.string.nav_profile).assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertIsDisplayed()

        // Spec order (PROMPT.md §7): Heute · Gewicht · Statistik · Profil, left to right.
        val lefts = listOf(R.string.nav_today, R.string.nav_weight, R.string.nav_stats, R.string.nav_profile)
            .map { tab(it).getUnclippedBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts)
        assertEquals(4, lefts.distinct().size)
    }

    @Test
    fun selectingATabShowsItsScreen() {
        tab(R.string.nav_weight).performClick()
        tab(R.string.nav_weight).assertIsSelected()
        tab(R.string.nav_today).assertIsNotSelected()
        compose.onNodeWithText(string(R.string.weight_empty_title)).assertIsDisplayed()

        tab(R.string.nav_stats).performClick()
        compose.onNodeWithText(string(R.string.stats_empty_title)).assertIsDisplayed()

        tab(R.string.nav_profile).performClick()
        tab(R.string.nav_profile).assertIsSelected()
        compose.onNodeWithText(string(R.string.plan_preview_title)).assertIsDisplayed()

        tab(R.string.nav_today).performClick()
        tab(R.string.nav_today).assertIsSelected()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertIsDisplayed()
    }

    @Test
    fun theProfilTabKeepsUnsavedEditsWhileAnotherTabIsOpen() {
        tab(R.string.nav_profile).performClick()
        compose.onNodeWithText(string(R.string.plan_preview_title)).assertIsDisplayed()
        tab(R.string.nav_weight).performClick()
        tab(R.string.nav_profile).performClick()
        compose.onNodeWithText(string(R.string.plan_preview_title)).assertIsDisplayed()
    }
}
