package de.privat.kalorienzaehler

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test: the real activity (Hilt entry point, theme, edge-to-edge) starts on "Heute". */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesOnTodayTab() {
        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.today_empty_title)).assertIsDisplayed()
    }
}
