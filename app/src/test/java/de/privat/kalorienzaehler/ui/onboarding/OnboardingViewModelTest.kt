package de.privat.kalorienzaehler.ui.onboarding

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val weights = FakeWeightRepository()
    private fun viewModel() = OnboardingViewModel(profiles, weights, TEST_CLOCK)

    private fun OnboardingViewModel.answerEverything() {
        edit { it.copy(sex = Sex.MALE, birthDate = "13.09.1996", heightCm = "180", weightKg = "80", activity = ActivityLevel.MODERATE) }
    }

    @Test
    fun startsOnTheWelcomePageAndMayAlwaysContinueFromThere() {
        val viewModel = viewModel()
        assertEquals(OnboardingStep.WELCOME, viewModel.step)
        assertTrue(viewModel.canContinue)
    }

    @Test
    fun eachStepWaitsForItsOwnAnswers() {
        val viewModel = viewModel()
        viewModel.next()
        assertEquals(OnboardingStep.BASICS, viewModel.step)
        assertFalse("no sex, no birth date", viewModel.canContinue)

        viewModel.next() // ignored while incomplete
        assertEquals(OnboardingStep.BASICS, viewModel.step)

        viewModel.edit { it.copy(sex = Sex.FEMALE) }
        assertFalse("birth date still missing", viewModel.canContinue)
        viewModel.edit { it.copy(birthDate = "13.09.1996") }
        assertTrue(viewModel.canContinue)

        viewModel.next()
        assertEquals(OnboardingStep.BODY, viewModel.step)
        assertFalse(viewModel.canContinue)
        viewModel.edit { it.copy(heightCm = "165", weightKg = "65") }
        assertTrue(viewModel.canContinue)

        viewModel.next()
        assertEquals(OnboardingStep.ACTIVITY, viewModel.step)
        assertFalse(viewModel.canContinue)
        viewModel.edit { it.copy(activity = ActivityLevel.LIGHT) }
        assertTrue(viewModel.canContinue)

        viewModel.next()
        assertEquals(OnboardingStep.GOAL, viewModel.step)
        assertTrue("maintenance needs no further input", viewModel.canContinue)
    }

    @Test
    fun implausibleInputBlocksTheStep() {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.edit { it.copy(sex = Sex.MALE, birthDate = "13.09.2035") }
        assertFalse("birth date in the future", viewModel.canContinue)
        viewModel.edit { it.copy(birthDate = "31.02.1990") }
        assertFalse("no such day", viewModel.canContinue)
    }

    @Test
    fun goalStepNeedsAValidGoalWeight() {
        val viewModel = viewModel()
        viewModel.answerEverything()
        repeat(4) { viewModel.next() }
        assertEquals(OnboardingStep.GOAL, viewModel.step)

        viewModel.edit { it.withGoalType(GoalType.LOSE) }
        assertFalse(viewModel.canContinue)
        viewModel.edit { it.copy(goalWeightKg = "85") }
        assertFalse("above the current weight", viewModel.canContinue)
        viewModel.edit { it.copy(goalWeightKg = "70") }
        assertTrue(viewModel.canContinue)
    }

    @Test
    fun backGoesOneStepAndStopsAtTheWelcomePage() {
        val viewModel = viewModel()
        viewModel.next()
        assertTrue(viewModel.back())
        assertEquals(OnboardingStep.WELCOME, viewModel.step)
        assertFalse(viewModel.back())
        assertEquals(OnboardingStep.WELCOME, viewModel.step)
    }

    @Test
    fun answersSurviveGoingBack() {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.edit { it.copy(sex = Sex.FEMALE, birthDate = "13.09.1996") }
        viewModel.next()
        viewModel.back()
        assertEquals(Sex.FEMALE, viewModel.draft.sex)
        assertEquals("13.09.1996", viewModel.draft.birthDate)
    }

    @Test
    fun finishSavesTheFirstWeightAndTheProfile() {
        val viewModel = viewModel()
        viewModel.answerEverything()
        viewModel.edit { it.withGoalType(GoalType.LOSE).copy(weeklyRateKg = 0.5, goalWeightKg = "70") }
        repeat(5) { viewModel.next() }
        assertEquals(OnboardingStep.SUMMARY, viewModel.step)
        assertEquals(2209.0, viewModel.evaluation.plan?.targetKcal ?: 0.0, 1e-9)

        viewModel.finish()

        assertEquals(listOf(WeightPoint(TEST_TODAY, 80.0)), weights.stored)
        val profile = assertNotNull(profiles.stored)
        assertEquals(Goal(GoalType.LOSE, 0.5, 70.0), profile.goal)
        assertEquals(Sex.MALE, profile.sex)
        assertFalse(viewModel.isSaving)
        assertFalse(viewModel.saveFailed)
    }

    @Test
    fun finishDoesNothingWhileTheAnswersAreIncomplete() {
        val viewModel = viewModel()
        viewModel.finish()
        assertNull(profiles.stored)
        assertTrue(weights.stored.isEmpty())
    }

    @Test
    fun aFailedSaveIsReportedAndCanBeRetried() {
        val viewModel = viewModel()
        viewModel.answerEverything()
        profiles.failSaves = true
        viewModel.finish()
        assertTrue(viewModel.saveFailed)
        assertFalse(viewModel.isSaving)
        assertNull(profiles.stored)

        profiles.failSaves = false
        viewModel.finish()
        assertFalse(viewModel.saveFailed)
        assertNotNull(profiles.stored)
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        assertNotNull("expected a value", value)
        return value!!
    }
}
