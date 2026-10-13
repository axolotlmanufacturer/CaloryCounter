package de.privat.kalorienzaehler.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun text(@StringRes id: Int): String = context.getString(id)

    private fun tab(@StringRes label: Int) =
        composeRule.onNode(hasText(text(label)) and hasClickAction())

    @Before
    fun setUp() {
        composeRule.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                KalorienzaehlerRoot()
            }
        }
    }

    @Test
    fun showsFourGermanTabsInOrderAndStartsOnHeute() {
        assertEquals(
            listOf("Heute", "Gewicht", "Statistik", "Profil"),
            listOf(R.string.nav_today, R.string.nav_weight, R.string.nav_stats, R.string.nav_profile).map(::text),
        )

        tab(R.string.nav_today).assertIsDisplayed().assertIsSelected()
        tab(R.string.nav_weight).assertIsDisplayed().assertIsNotSelected()
        tab(R.string.nav_stats).assertIsDisplayed().assertIsNotSelected()
        tab(R.string.nav_profile).assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithText(text(R.string.today_empty_title)).assertIsDisplayed()

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
        composeRule.onNodeWithText(text(R.string.weight_empty_title)).assertIsDisplayed()

        tab(R.string.nav_stats).performClick()
        composeRule.onNodeWithText(text(R.string.stats_empty_title)).assertIsDisplayed()

        tab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(text(R.string.profile_empty_title)).assertIsDisplayed()

        tab(R.string.nav_today).performClick()
        tab(R.string.nav_today).assertIsSelected()
        composeRule.onNodeWithText(text(R.string.today_empty_title)).assertIsDisplayed()
    }
}
