package de.privat.kalorienzaehler.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.profile.ProfileDraft
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.clippedTexts
import de.privat.kalorienzaehler.ui.onboarding.OnboardingContent
import de.privat.kalorienzaehler.ui.onboarding.OnboardingStep
import de.privat.kalorienzaehler.ui.settings.ProfileContent
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PROMPT.md §8: font scaling up to 200 % without clipping, on a small phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 2.0f, qualifiers = "+w360dp-h640dp-xxhdpi")
class FontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val draft = ProfileDraft(
        sex = Sex.MALE,
        birthDate = "13.09.1996",
        heightCm = "180",
        weightKg = "80",
        activity = ActivityLevel.MODERATE,
    ).withGoalType(GoalType.LOSE).copy(goalWeightKg = "70")

    @Test
    fun everyOnboardingStepFitsAtTwiceTheFontSize() {
        var step by mutableStateOf(OnboardingStep.WELCOME)
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                OnboardingContent(
                    step = step,
                    draft = draft,
                    evaluation = draft.evaluate(TEST_TODAY),
                    canContinue = true,
                    isSaving = false,
                    saveFailed = false,
                    edit = {},
                    onNext = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }
        OnboardingStep.entries.forEach { current ->
            step = current
            compose.waitForIdle()
            assertEquals("clipped texts in step $current", emptyList<String>(), compose.clippedTexts())
        }
    }

    @Test
    fun theProfilScreenFitsAtTwiceTheFontSize() {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                ProfileContent(
                    isLoaded = true,
                    draft = draft,
                    evaluation = draft.evaluate(TEST_TODAY),
                    canSave = true,
                    edit = {},
                    onSave = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        assertEquals("clipped texts", emptyList<String>(), compose.clippedTexts())
    }
}
