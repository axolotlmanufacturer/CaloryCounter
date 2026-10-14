package de.privat.kalorienzaehler.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.privat.kalorienzaehler.MainActivity
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.FoodPortion
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeFavoriteRepository
import de.privat.kalorienzaehler.testing.FakeFoodRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.testBls
import de.privat.kalorienzaehler.testing.testOff
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testFood
import de.privat.kalorienzaehler.testing.testQuickEntry
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The whole diary through the real app: Heute → add food → back on Heute. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "+w411dp-h891dp-xxhdpi")
class DiaryFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var profiles: FakeProfileRepository

    @Inject lateinit var weights: FakeWeightRepository

    @Inject lateinit var diary: FakeDiaryRepository

    @Inject lateinit var foods: FakeCustomFoodRepository

    @Inject lateinit var database: FakeFoodRepository

    @Inject lateinit var favorites: FakeFavoriteRepository

    @Before
    fun setUp() {
        hilt.inject()
        profiles.preset(sampleProfile())
        weights.preset(WeightPoint(TEST_TODAY, 80.0))
    }

    private fun text(id: Int, vararg args: Any) = string(id, *args)

    private fun tab(id: Int) = compose.onNode(hasText(text(id)) and isSelectable())

    private fun addTo(meal: Int) =
        compose.onNodeWithContentDescription(text(R.string.today_add_to_meal, text(meal))).performClick()

    /** The search starts after a short pause in typing: wait until [name] is listed. */
    private fun waitForFood(name: String) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun aQuickEntryEndsUpInTheMealAndInTheDaySum() {
        addTo(R.string.meal_breakfast)
        compose.onNodeWithText(text(R.string.add_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.add_subtitle, text(R.string.meal_breakfast), text(R.string.date_today))).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.add_quick_entry)).performClick()
        compose.onNodeWithText(text(R.string.quick_label)).performTextInput("Kaffee mit Milch")
        compose.onNodeWithText(text(R.string.field_energy_kcal)).performTextInput("42,5")
        compose.onNodeWithText(text(R.string.portion_add)).performClick()

        tab(R.string.nav_today).assertIsSelected()
        compose.onNodeWithText("Kaffee mit Milch").assertIsDisplayed()
        val saved = diary.stored.single()
        assertEquals(FoodSource.QUICK, saved.source)
        assertEquals(Meal.FRUEHSTUECK, saved.meal)
        assertEquals(TEST_TODAY, saved.date)
        assertEquals(42.5, saved.nutrients.kcal, 0.0)
        assertEquals(null, saved.nutrients.protein)
    }

    @Test
    fun aQuickEntryWithoutAnythingTypedCannotBeAdded() {
        addTo(R.string.meal_snacks)
        compose.onNodeWithText(text(R.string.add_quick_entry)).performClick()
        compose.onNodeWithText(text(R.string.portion_add)).assertIsNotEnabled()
    }

    @Test
    fun aNewCustomFoodGoesStraightToTheAmountAndIsLogged() {
        addTo(R.string.meal_lunch)
        compose.onNodeWithText(text(R.string.add_create_custom)).performClick()
        compose.onNodeWithText(text(R.string.custom_name)).performTextInput("Haferflocken")
        compose.onNodeWithText(text(R.string.field_energy_kcal)).performScrollTo().performTextInput("372")
        compose.onNodeWithText(text(R.string.field_protein_g)).performScrollTo().performTextInput("13,5")
        compose.onNodeWithText(text(R.string.action_save)).performClick()

        // Now on the amount screen for the new food: 100 g, 372 kcal.
        compose.onNodeWithText(text(R.string.portion_title)).assertIsDisplayed()
        compose.onNodeWithText("Haferflocken").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.portion_amount)).performTextReplacement("50")
        compose.onNodeWithText(text(R.string.portion_add)).performClick()

        tab(R.string.nav_today).assertIsSelected()
        compose.onNodeWithText("Haferflocken").assertIsDisplayed()
        compose.onNodeWithText("50 g").assertIsDisplayed()
        val entry = diary.stored.single()
        assertEquals(Meal.MITTAGESSEN, entry.meal)
        assertEquals(186.0, entry.nutrients.kcal, 1e-9)
        assertEquals(foods.stored.single().id.toString(), entry.foodRef)
    }

    @Test
    fun anExistingOwnFoodIsLoggedInThreeTaps() {
        foods.preset(testFood(name = "Haferflocken", brand = "Kölln", kcal = 372.0, servingG = 40.0))
        addTo(R.string.meal_breakfast)
        compose.onNodeWithText("Haferflocken").performClick() // 1: the food (own foods are listed first)
        compose.onNodeWithText(text(R.string.portion_add)).performClick() // 2: add, one portion preselected

        tab(R.string.nav_today).assertIsSelected()
        val entry = diary.stored.single()
        assertEquals("Portion (40 g)", entry.unit)
        assertEquals(1.0, entry.amount, 0.0)
        assertEquals(148.8, entry.nutrients.kcal, 1e-9)
    }

    @Test
    fun searchFindsOwnFoodsAcrossUmlauts() {
        foods.preset(testFood(name = "Käsebrot", brand = null), testFood(name = "Apfelmus", brand = null))
        addTo(R.string.meal_breakfast)
        compose.onNodeWithText(text(R.string.add_search_label)).performTextInput("kaese")
        waitForFood("Käsebrot")
        compose.onNodeWithText("Käsebrot").assertIsDisplayed()
        compose.onNodeWithText("Apfelmus").assertDoesNotExist()
    }

    @Test
    fun aDatabaseFoodIsFoundAndLoggedWithAnEstimatedPortion() {
        val apple = testBls("F110100", "Apfel roh", kcal = 58.0)
        database.preset(apple, testOff("4000540000108", "Haferflocken", "Kölln", kcal = 361.0))
        database.presetPortions(apple, FoodPortion("Stück (mittel)", 150.0, estimated = true))
        addTo(R.string.meal_lunch)
        compose.onNodeWithText(text(R.string.add_search_label)).performTextInput("apfel")
        waitForFood("Apfel roh")
        compose.onNodeWithText(text(R.string.food_source_bls)).assertIsDisplayed()
        compose.onNodeWithText("Haferflocken").assertDoesNotExist()
        compose.onNodeWithText("Apfel roh").performClick()

        // The amount screen offers g and the estimated portion, marked as an estimate.
        compose.onNodeWithText(text(R.string.portion_title)).assertIsDisplayed()
        val portionChip = text(R.string.portion_unit_label_estimated, "Stück (mittel)", text(R.string.unit_g_value, "150"))
        compose.onNodeWithText(portionChip).performClick()
        compose.onNodeWithText(text(R.string.portion_estimated_hint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.portion_amount)).performTextReplacement("2")
        compose.onNodeWithText(text(R.string.portion_add)).performClick()

        tab(R.string.nav_today).assertIsSelected()
        val entry = diary.stored.single()
        assertEquals(FoodSource.BLS, entry.source)
        assertEquals("F110100", entry.foodRef)
        assertEquals("Stück (mittel)", entry.unit)
        assertEquals(300.0, entry.grams, 1e-9)
        assertEquals(174.0, entry.nutrients.kcal, 1e-9)
        assertEquals(Meal.MITTAGESSEN, entry.meal)
    }

    @Test
    fun anOpenFoodFactsFoodShowsItsBrandAndBadge() {
        database.preset(testOff("4000540000108", "Haferflocken", "Kölln", kcal = 361.0))
        addTo(R.string.meal_breakfast)
        compose.onNodeWithText(text(R.string.add_search_label)).performTextInput("hafer")
        waitForFood("Haferflocken")
        compose.onNodeWithText(text(R.string.food_source_off)).assertIsDisplayed()
        compose.onNodeWithText("Kölln", substring = true).assertIsDisplayed()
    }

    @Test
    fun aFoodStarredOnTheAmountScreenAppearsUnderFavoriten() {
        val gouda = testBls("M402600", "Gouda 48 % Fett i. Tr.", kcal = 379.0)
        database.preset(gouda)
        addTo(R.string.meal_dinner)
        compose.onNodeWithText(text(R.string.add_search_label)).performTextInput("gouda")
        waitForFood("Gouda 48 % Fett i. Tr.")
        compose.onNodeWithText("Gouda 48 % Fett i. Tr.").performClick()
        compose.onNodeWithContentDescription(text(R.string.favorite_add)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(text(R.string.favorite_remove)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(setOf(gouda.key), favorites.stored)

        pressBack() // back to the add screen; the search is still there, the chip switches to Favoriten
        compose.onNodeWithText(text(R.string.add_chip_favorites)).performClick()
        compose.onNodeWithText("Gouda 48 % Fett i. Tr.").assertIsDisplayed()
    }

    @Test
    fun whileTheDatabaseIsPreparedTheOwnFoodsStillAppearAndAHintIsShown() {
        database.setStatus(FoodDbStatus.Preparing)
        foods.preset(testFood(name = "Käsebrot", brand = null))
        addTo(R.string.meal_breakfast)
        compose.onNodeWithText(text(R.string.add_search_label)).performTextInput("kaese")
        waitForFood("Käsebrot")
        compose.onNodeWithText(text(R.string.add_db_preparing)).assertIsDisplayed()
        database.setStatus(FoodDbStatus.Ready)
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text(R.string.add_db_preparing)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun anUnreadableDatabaseIsExplained() {
        database.setStatus(FoodDbStatus.Failed)
        addTo(R.string.meal_breakfast)
        compose.onNodeWithText(text(R.string.add_db_failed)).assertIsDisplayed()
    }

    @Test
    fun theAmountOfALoggedEntryCanBeChanged() {
        diary.preset(testEntry(name = "Haferbrei", grams = 150.0))
        compose.onNodeWithText("Haferbrei").performClick()
        compose.onNodeWithText(text(R.string.portion_title_edit)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.portion_amount)).performTextReplacement("300")
        compose.onNodeWithText(text(R.string.action_save)).performClick()

        tab(R.string.nav_today).assertIsSelected()
        compose.onNodeWithText("300 g").assertIsDisplayed()
        assertEquals(600.0, diary.stored.single().nutrients.kcal, 1e-9)
    }

    @Test
    fun aQuickEntryCanBeChanged() {
        diary.preset(testQuickEntry(name = "Imbiss", nutrients = NutrientAmounts(250.0)))
        compose.onNodeWithText("Imbiss").performClick()
        compose.onNodeWithText(text(R.string.quick_title_edit)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.field_energy_kcal)).performTextReplacement("300")
        compose.onNodeWithText(text(R.string.action_save)).performClick()

        tab(R.string.nav_today).assertIsSelected()
        assertEquals(300.0, diary.stored.single().nutrients.kcal, 0.0)
    }

    @Test
    fun deletingAnEntryCanBeUndone() {
        diary.preset(testEntry(name = "Haferbrei"))
        compose.onNodeWithText("Haferbrei").performTouchInput { swipeLeft() }

        compose.onNodeWithText(text(R.string.today_entry_deleted)).assertIsDisplayed()
        compose.onNodeWithText("Haferbrei").assertDoesNotExist()
        assertEquals(emptyList<Any>(), diary.stored)

        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        // The undo runs on a background coroutine of the ViewModel: wait until it has put the entry back.
        compose.waitUntil { diary.stored.size == 1 }
        compose.onNodeWithText("Haferbrei").assertIsDisplayed()
        assertEquals(1, diary.stored.size)
    }

    @Test
    fun yesterdaysMealIsCopiedAndTheCopyCanBeUndone() {
        diary.preset(
            testEntry(date = TEST_TODAY.minusDays(1), meal = Meal.FRUEHSTUECK, name = "Müsli"),
            testEntry(date = TEST_TODAY.minusDays(1), meal = Meal.FRUEHSTUECK, name = "Kaffee"),
        )
        compose.onNodeWithText(text(R.string.today_copy_yesterday, 2)).performClick()

        compose.onNodeWithText("Müsli").assertIsDisplayed()
        compose.onNodeWithText("Kaffee").assertIsDisplayed()
        assertEquals(4, diary.stored.size)
        compose.onNodeWithText(
            compose.activity.resources.getQuantityString(R.plurals.today_entries_copied, 2, 2),
        ).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        assertEquals(2, diary.stored.size)
        assertNotNull(diary.stored.firstOrNull { it.date == TEST_TODAY.minusDays(1) })
    }

    @Test
    fun backLeavesTheAddFlowStepByStep() {
        addTo(R.string.meal_dinner)
        compose.onNodeWithText(text(R.string.add_quick_entry)).performClick()
        compose.onNodeWithText(text(R.string.quick_label)).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText(text(R.string.add_title)).assertIsDisplayed()

        pressBack()
        tab(R.string.nav_today).assertIsSelected()
        compose.onNodeWithText(text(R.string.today_empty_title)).assertIsDisplayed()
    }

    @Test
    fun theBottomBarIsHiddenInsideTheFlow() {
        addTo(R.string.meal_dinner)
        compose.onNode(hasText(text(R.string.nav_weight)) and isSelectable()).assertDoesNotExist()
    }

    @Test
    fun anOwnFoodCanBeEditedAndDeletedFromTheAddScreen() {
        val food = foods.preset(testFood(name = "Haferflocken")).single()
        addTo(R.string.meal_breakfast)
        compose.onNodeWithContentDescription(text(R.string.food_edit_description, "Haferflocken")).performClick()
        compose.onNodeWithText(text(R.string.custom_title_edit)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.custom_name)).performTextReplacement("Haferflocken zart")
        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.onNodeWithText("Haferflocken zart").assertIsDisplayed()
        assertEquals("Haferflocken zart", foods.stored.single().name)

        compose.onNodeWithContentDescription(text(R.string.food_edit_description, "Haferflocken zart")).performClick()
        compose.onNodeWithText(text(R.string.custom_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.action_delete)).performClick()
        compose.onNodeWithText(text(R.string.add_title)).assertIsDisplayed()
        assertEquals(emptyList<Any>(), foods.stored)
        assertEquals(food.id, food.id)
    }
}
