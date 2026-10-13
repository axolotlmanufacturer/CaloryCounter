package de.privat.kalorienzaehler.ui.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.goal.GoalPlanner
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.dayData
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testQuickEntry
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+w411dp-h891dp-xxhdpi")
class TodayContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val yesterday = TEST_TODAY.minusDays(1)
    private val diary = MutableStateFlow<List<DiaryEntry>>(emptyList())

    private val plan = GoalPlanner.plan(sampleProfile(), 80.0, TEST_TODAY).let { DayPlan(it.targetKcal, it.macros) }

    private val added = mutableListOf<Pair<LocalDate, Meal>>()
    private val edited = mutableListOf<DiaryEntry>()
    private val deleted = mutableListOf<DiaryEntry>()
    private val copied = mutableListOf<Pair<LocalDate, Meal>>()

    private fun day(date: LocalDate): Flow<DayData> = diary.map { all ->
        dayData(date, all.filter { it.date == date }, all.filter { it.date == date.minusDays(1) })
    }

    private fun show(header: TodayHeader = TodayHeader(EnergyUnit.KCAL, plan)) {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                TodayContent(
                    header = header,
                    today = TEST_TODAY,
                    dayFlow = ::day,
                    snackbarHostState = SnackbarHostState(),
                    onAddFood = { date, meal -> added += date to meal },
                    onEditEntry = { edited += it },
                    onDeleteEntry = { deleted += it },
                    onCopyFromPreviousDay = { date, meal -> copied += date to meal },
                )
            }
        }
    }

    private fun kcal(value: String) = string(R.string.unit_kcal_value, value)

    private fun exactly(text: String) = hasText(text, substring = false)

    @Test
    fun showsTargetEatenAndWhatIsLeft() {
        // 400 + 575 = 975 kcal eaten of 2.209.
        diary.value = listOf(
            testQuickEntry(id = 1, meal = Meal.FRUEHSTUECK, nutrients = NutrientAmounts(400.0)),
            testQuickEntry(id = 2, meal = Meal.MITTAGESSEN, nutrients = NutrientAmounts(575.0)),
        )
        show()
        compose.onNodeWithText("1.234").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_ring_remaining, "kcal")).assertIsDisplayed()
        compose.onNodeWithText(kcal("2.209")).assertIsDisplayed()
        compose.onNode(exactly(kcal("975"))).assertIsDisplayed()
    }

    @Test
    fun theRingIsDescribedForTalkBack() {
        diary.value = listOf(testQuickEntry(id = 1, nutrients = NutrientAmounts(975.0)))
        show()
        compose.onNode(
            hasContentDescription(
                string(R.string.today_ring_description_remaining, kcal("1.234"), kcal("2.209"), kcal("975")),
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun goingOverTheTargetIsShownCalmlyAsHowMuchOver() {
        diary.value = listOf(testQuickEntry(id = 1, nutrients = NutrientAmounts(2500.0)))
        show()
        compose.onNodeWithText("291").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_ring_over, "kcal")).assertIsDisplayed()
    }

    @Test
    fun theEnergyUnitFollowsTheProfile() {
        diary.value = listOf(testQuickEntry(id = 1, nutrients = NutrientAmounts(975.0)))
        show(TodayHeader(EnergyUnit.KJ, plan))
        // 1.234 kcal left = 5.163 kJ; target 2.209 kcal = 9.242 kJ.
        compose.onNodeWithText("5.163").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_ring_remaining, "kJ")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.unit_kj_value, "9.242")).assertIsDisplayed()
    }

    @Test
    fun withoutATargetOnlyWhatWasEatenIsShown() {
        diary.value = listOf(testQuickEntry(id = 1, nutrients = NutrientAmounts(975.0)))
        show(TodayHeader(EnergyUnit.KCAL, plan = null))
        compose.onNodeWithText("975").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_ring_eaten, "kcal")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_target)).assertDoesNotExist()
    }

    @Test
    fun macroBarsShowEatenAgainstTarget() {
        diary.value = listOf(
            testEntry(id = 1, grams = 100.0), // 10 g protein, 30 g carbs, 5 g fat
        )
        show()
        compose.onNodeWithText(string(R.string.macro_progress_value, "10 g", "128 g")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.macro_protein)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.macro_carbs)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.macro_fat)).assertIsDisplayed()
    }

    @Test
    fun entriesWithoutMacrosAreFlaggedInsteadOfCountedAsZero() {
        diary.value = listOf(testQuickEntry(id = 1, nutrients = NutrientAmounts(250.0)))
        show()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertDoesNotExist()
        compose.onNode(hasText("Bei einem Eintrag fehlen", substring = true)).assertIsDisplayed()
    }

    @Test
    fun anEmptyDayInvitesToTapPlus() {
        show()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_empty_message)).assertIsDisplayed()
    }

    @Test
    fun mealsListTheirEntriesWithAmountAndEnergy() {
        diary.value = listOf(
            testEntry(id = 1, meal = Meal.FRUEHSTUECK, name = "Haferbrei", brand = "Kölln", grams = 150.0),
        )
        show()
        compose.onNodeWithText(string(R.string.meal_breakfast)).assertIsDisplayed()
        compose.onNodeWithText("Haferbrei").assertIsDisplayed()
        compose.onNodeWithText("Kölln · 150 g").assertIsDisplayed()
    }

    @Test
    fun theSameEnergyAppearsInTheRingTheMealAndTheEntry() {
        diary.value = listOf(testEntry(id = 1, grams = 150.0))
        show()
        // Eaten (ring), meal sum and entry: 300 kcal each.
        assertEquals(3, compose.onAllNodes(exactly(kcal("300"))).fetchSemanticsNodes().size)
    }

    @Test
    fun plusAddsFoodToTheMealOfTheDayShown() {
        show()
        compose.onNodeWithContentDescription(string(R.string.today_add_to_meal, string(R.string.meal_lunch))).performClick()
        assertEquals(listOf(TEST_TODAY to Meal.MITTAGESSEN), added)
    }

    @Test
    fun tappingAnEntryOpensItForEditing() {
        val entry = testEntry(id = 5, name = "Haferbrei")
        diary.value = listOf(entry)
        show()
        compose.onNodeWithText("Haferbrei").performClick()
        assertEquals(listOf(entry), edited)
    }

    @Test
    fun swipingAnEntryAwayDeletesIt() {
        val entry = testEntry(id = 5, name = "Haferbrei")
        diary.value = listOf(entry)
        show()
        compose.onNodeWithText("Haferbrei").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(entry), deleted)
    }

    @Test
    fun aDeletedRowComesBackAtRestWhenItIsRestored() {
        val entry = testEntry(id = 5, name = "Haferbrei")
        diary.value = listOf(entry)
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                TodayContent(
                    header = TodayHeader(EnergyUnit.KCAL, plan),
                    today = TEST_TODAY,
                    dayFlow = ::day,
                    snackbarHostState = SnackbarHostState(),
                    onAddFood = { _, _ -> },
                    onEditEntry = {},
                    onDeleteEntry = { diary.value = diary.value - it },
                    onCopyFromPreviousDay = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Haferbrei").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Haferbrei").assertDoesNotExist()
        // Swiping a row away does not change the day.
        compose.onNodeWithText(string(R.string.date_tomorrow)).assertDoesNotExist()

        diary.value = listOf(entry)
        compose.waitForIdle()
        compose.onNodeWithText("Haferbrei").assertIsDisplayed()
        // At rest, not swiped away: tapping it still opens it.
        compose.onNodeWithText("Haferbrei").performClick()
    }

    @Test
    fun yesterdaysMealCanBeCopiedIntoAnEmptyMeal() {
        diary.value = listOf(
            testEntry(id = 1, date = yesterday, meal = Meal.FRUEHSTUECK),
            testEntry(id = 2, date = yesterday, meal = Meal.FRUEHSTUECK),
        )
        show()
        compose.onNodeWithText(string(R.string.today_copy_yesterday, 2)).performClick()
        assertEquals(listOf(TEST_TODAY to Meal.FRUEHSTUECK), copied)
        // Lunch had nothing yesterday: nothing to copy.
        compose.onNodeWithText(string(R.string.today_copy_yesterday, 0)).assertDoesNotExist()
    }

    @Test
    fun aMealThatAlreadyHasEntriesOffersNoCopy() {
        diary.value = listOf(
            testEntry(id = 1, date = yesterday, meal = Meal.FRUEHSTUECK),
            testEntry(id = 2, date = TEST_TODAY, meal = Meal.FRUEHSTUECK),
        )
        show()
        compose.onNodeWithText(string(R.string.today_copy_yesterday, 1)).assertDoesNotExist()
    }

    @Test
    fun theDateSwitcherStepsThroughTheDays() {
        show()
        compose.onNodeWithText(string(R.string.date_today)).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.today_next_day)).performClick()
        compose.onNodeWithText(string(R.string.date_tomorrow)).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).performClick()
        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).performClick()
        compose.onNodeWithText(string(R.string.date_yesterday)).assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).performClick()
        // Sunday, 11 October 2026.
        compose.onNodeWithText("So., 11.10.2026").assertIsDisplayed()
    }

    @Test
    fun aDayInThePastShowsItsOwnEntriesAndAWayBackToToday() {
        diary.value = listOf(
            testEntry(id = 1, date = TEST_TODAY, name = "Heutiges"),
            testEntry(id = 2, date = yesterday, name = "Gestriges"),
        )
        show()
        compose.onNodeWithText("Heutiges").assertIsDisplayed()

        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).performClick()
        compose.onNodeWithText("Gestriges").assertIsDisplayed()
        compose.onNodeWithText("Heutiges").assertDoesNotExist()

        compose.onNode(hasText(string(R.string.date_today)) and hasClickAction()).performClick()
        compose.onNodeWithText("Heutiges").assertIsDisplayed()
    }

    @Test
    fun addingFoodToAnotherDayUsesThatDay() {
        show()
        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).performClick()
        compose.onNodeWithContentDescription(string(R.string.today_add_to_meal, string(R.string.meal_snacks))).performClick()
        assertEquals(listOf(yesterday to Meal.SNACKS), added)
    }

    @Test
    fun yesterdaysCopyButtonSaysVomVortagOnOtherDays() {
        diary.value = listOf(testEntry(id = 1, date = yesterday.minusDays(1), meal = Meal.ABENDESSEN))
        show()
        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).performClick()
        compose.onNodeWithText(string(R.string.today_copy_previous_day, 1)).assertIsDisplayed()
    }

    @Test
    fun theDatePickerOpensAndCanBeDismissed() {
        show()
        compose.onNodeWithContentDescription(string(R.string.today_previous_day)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.date_today)) and hasClickAction()).performClick()
        compose.onNodeWithText(string(R.string.action_ok)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_cancel)).performClick()
        compose.onNodeWithText(string(R.string.action_ok)).assertDoesNotExist()
    }
}
