package de.privat.kalorienzaehler.ui.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeFoodRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+w411dp-h891dp-xxhdpi")
class OnboardingFlowTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val profiles = FakeProfileRepository()
    private val weights = FakeWeightRepository()
    private val foodDb = FakeFoodRepository()

    @Before
    fun setUp() {
        val viewModel = OnboardingViewModel(profiles, weights, foodDb, TEST_CLOCK)
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) { OnboardingScreen(viewModel) }
        }
    }

    /** Buttons live in the bottom bar, outside the scrolling content. */
    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    /** Options and fields live in the scrolling content. */
    private fun choose(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    private fun type(label: String, text: String) = compose.onNodeWithText(label).performScrollTo().performTextInput(text)

    private fun next() = tap(string(R.string.action_next))

    private fun answerBasics(sex: String = string(R.string.sex_male), birthDate: String = "13.09.1996") {
        tap(string(R.string.onboarding_welcome_start))
        choose(sex)
        type(string(R.string.field_birth_date), birthDate)
        next()
    }

    private fun answerBodyAndActivity(heightCm: String = "180", weightKg: String = "80", activity: Int = R.string.activity_moderate) {
        type(string(R.string.field_height), heightCm)
        type(string(R.string.field_weight), weightKg)
        next()
        choose(string(activity))
        next()
    }

    @Test
    fun welcomePageOffersToStart() {
        compose.onNodeWithText(string(R.string.onboarding_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.onboarding_welcome_start)).assertIsEnabled()
    }

    @Test
    fun theWelcomePageMentionsTheFoodDatabaseWhileItIsBeingPrepared() {
        val message = string(R.string.onboarding_food_db_preparing)
        compose.onAllNodesWithText(message).assertCountEquals(0)
        foodDb.setStatus(FoodDbStatus.Preparing)
        compose.waitUntil { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(message).assertIsDisplayed()
        // Starting is not blocked by it.
        compose.onNodeWithText(string(R.string.onboarding_welcome_start)).assertIsEnabled()
        foodDb.setStatus(FoodDbStatus.Ready)
        compose.waitUntil { compose.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theContinueButtonWaitsForTheAnswers() {
        tap(string(R.string.onboarding_welcome_start))
        compose.onNodeWithText(string(R.string.action_next)).assertIsNotEnabled()
        choose(string(R.string.sex_male))
        compose.onNodeWithText(string(R.string.action_next)).assertIsNotEnabled()
        type(string(R.string.field_birth_date), "13.09.1996")
        compose.onNodeWithText(string(R.string.action_next)).assertIsEnabled()
    }

    @Test
    fun anUnreadableDateIsExplainedAndBlocksTheStep() {
        tap(string(R.string.onboarding_welcome_start))
        choose(string(R.string.sex_male))
        type(string(R.string.field_birth_date), "13.13.1996")
        compose.onNodeWithText(string(R.string.error_date)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_next)).assertIsNotEnabled()
    }

    @Test
    fun theFirstSpecVectorFromTypingToTheSavedProfile() {
        answerBasics()
        answerBodyAndActivity()

        choose(string(R.string.goal_lose))
        choose(string(R.string.goal_rate_chip, "0,5"))
        type(string(R.string.field_goal_weight), "70")
        next()

        compose.onNodeWithText(string(R.string.onboarding_summary_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.unit_kcal_value, "1.780")).assertIsDisplayed() // Grundumsatz
        compose.onNodeWithText(string(R.string.unit_kcal_value, "2.759")).assertIsDisplayed() // Gesamtumsatz
        compose.onNodeWithText(string(R.string.unit_kcal_value, "2.209")).assertIsDisplayed() // Tagesziel
        compose.onNodeWithText(string(R.string.plan_goal_date_value, "02.03.2027")).performScrollTo().assertIsDisplayed()

        tap(string(R.string.action_done))

        val profile = profiles.stored!!
        assertEquals(Sex.MALE, profile.sex)
        assertEquals(Goal(GoalType.LOSE, 0.5, 70.0), profile.goal)
        assertEquals(180.0, profile.heightCm, 0.0)
        assertEquals(listOf(WeightPoint(TEST_TODAY, 80.0)), weights.stored)
    }

    @Test
    fun theSecondSpecVectorShowsTheFloorAndTheRealisticRate() {
        answerBasics(sex = string(R.string.sex_female))
        answerBodyAndActivity(heightCm = "165", weightKg = "65", activity = R.string.activity_light)

        choose(string(R.string.goal_lose))
        choose(string(R.string.goal_rate_chip, "0,75"))
        type(string(R.string.field_goal_weight), "58")
        next()

        compose.onNodeWithText(string(R.string.unit_kcal_value, "1.200")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.notice_rate_capped, "0,75", "0,65")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.notice_floor_applied, "0,62", "1.200")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun keepingTheWeightGivesTheTotalExpenditureAsTarget() {
        answerBasics()
        answerBodyAndActivity()
        next() // goal: Halten is the default

        compose.onNodeWithText(string(R.string.plan_target_maintain)).assertIsDisplayed()
        tap(string(R.string.action_done))
        assertEquals(GoalType.MAINTAIN, profiles.stored?.goal?.type)
    }

    @Test
    fun minorsCannotPickAWeightGoal() {
        answerBasics(birthDate = "13.09.2010")
        answerBodyAndActivity(heightCm = "170", weightKg = "60")

        compose.onNodeWithText(string(R.string.goal_not_allowed_hint)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.goal_lose)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.goal_gain)).assertIsNotEnabled()
        next()
        compose.onNodeWithText(string(R.string.notice_minor_estimate)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun underweightPeopleAreNotOfferedToLoseButMayGain() {
        answerBasics()
        answerBodyAndActivity(heightCm = "180", weightKg = "58") // BMI 17,9

        compose.onNodeWithText(string(R.string.goal_lose_not_offered_hint)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.goal_lose)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.goal_gain)).assertIsEnabled()
    }

    @Test
    fun aGoalWeightBelowBmi185IsRefusedWithAnExplanation() {
        answerBasics()
        answerBodyAndActivity()
        choose(string(R.string.goal_lose))
        type(string(R.string.field_goal_weight), "55")

        compose.onNodeWithText(string(R.string.error_goal_weight_below_bmi, "60,0")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_next)).assertIsNotEnabled()
    }

    @Test
    fun backReturnsToThePreviousQuestionKeepingTheAnswers() {
        answerBasics()
        tap(string(R.string.action_back))
        compose.onNodeWithText(string(R.string.field_birth_date)).assertIsDisplayed()
        compose.onNodeWithText("13.09.1996").assertIsDisplayed()
    }

    @Test
    fun nothingIsStoredBeforeTheLastStep() {
        answerBasics()
        answerBodyAndActivity()
        assertNull(profiles.stored)
        assertEquals(emptyList<WeightPoint>(), weights.stored)
    }
}
