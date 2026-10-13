package de.privat.kalorienzaehler.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.domain.diary.CustomFoodDraft
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.domain.diary.QuickEntryDraft
import de.privat.kalorienzaehler.domain.diary.AmountUnit
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.goal.GoalPlanner
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.testing.clippedTexts
import de.privat.kalorienzaehler.testing.scrollListTo
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.testing.dayData
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testFood
import de.privat.kalorienzaehler.testing.testQuickEntry
import de.privat.kalorienzaehler.ui.addfood.AddFilter
import de.privat.kalorienzaehler.ui.addfood.AddFoodContent
import de.privat.kalorienzaehler.ui.addfood.AddFoodState
import de.privat.kalorienzaehler.ui.addfood.AddMode
import de.privat.kalorienzaehler.ui.addfood.CustomFoodContent
import de.privat.kalorienzaehler.ui.addfood.PortionContent
import de.privat.kalorienzaehler.ui.addfood.PortionSubject
import de.privat.kalorienzaehler.ui.addfood.QuickEntryContent
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import de.privat.kalorienzaehler.ui.today.DayPlan
import de.privat.kalorienzaehler.ui.today.TodayContent
import de.privat.kalorienzaehler.ui.today.TodayHeader
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PROMPT.md §8 for the diary screens: font scaling up to 200 % without clipping, on a small phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 2.0f, qualifiers = "+w360dp-h640dp-xxhdpi")
class DiaryFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val plan = GoalPlanner.plan(sampleProfile(), 80.0, TEST_TODAY).let { DayPlan(it.targetKcal, it.macros) }
    private val snackbar = SnackbarHostState()

    private fun assertNothingClipped(label: String) =
        assertEquals("clipped texts on $label", emptyList<String>(), compose.clippedTexts())

    private fun heute(entries: List<DiaryEntry>, previous: List<DiaryEntry>, unit: EnergyUnit = EnergyUnit.KCAL) {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                TodayContent(
                    header = TodayHeader(unit, plan),
                    today = TEST_TODAY,
                    dayFlow = { date -> flowOf(dayData(date, entries.filter { it.date == date }, previous)) },
                    snackbarHostState = snackbar,
                    onAddFood = { _, _ -> },
                    onEditEntry = {},
                    onDeleteEntry = {},
                    onCopyFromPreviousDay = { _, _ -> },
                )
            }
        }
    }

    private val longEntries = listOf(
        testEntry(id = 1, meal = Meal.FRUEHSTUECK, name = "Haferflocken zart mit Vollkorn und Beeren", brand = "Kölln Müsli Manufaktur", grams = 150.0),
        testQuickEntry(id = 2, meal = Meal.MITTAGESSEN, name = "Mittagessen in der Kantine", nutrients = NutrientAmounts(850.0)),
        testEntry(id = 3, meal = Meal.ABENDESSEN, name = "Brot", grams = 100.0),
    )

    @Test
    fun heuteFitsAtTwiceTheFontSize() {
        heute(longEntries, previous = emptyList())
        assertNothingClipped("Heute (top)")
        // The entries are below the fold at this size: scroll to each and look again.
        longEntries.forEach { entry ->
            compose.scrollListTo(entry.name)
            assertNothingClipped("Heute at ${entry.name}")
        }
    }

    @Test
    fun heuteInKilojoulesFitsToo() {
        heute(longEntries, previous = emptyList(), unit = EnergyUnit.KJ)
        assertNothingClipped("Heute in kJ")
        compose.scrollListTo(longEntries.first().name)
        assertNothingClipped("Heute in kJ at the first entry")
    }

    @Test
    fun anEmptyHeuteWithTheCopyButtonsFits() {
        heute(
            entries = emptyList(),
            previous = listOf(
                testEntry(id = 1, meal = Meal.FRUEHSTUECK),
                testEntry(id = 2, meal = Meal.MITTAGESSEN),
                testEntry(id = 3, meal = Meal.ABENDESSEN),
            ),
        )
        assertNothingClipped("empty Heute")
        compose.scrollListTo(string(R.string.today_copy_yesterday, 1))
        assertNothingClipped("empty Heute at the copy button")
    }

    @Test
    fun heuteOverTheTargetFits() {
        heute(listOf(testQuickEntry(id = 1, nutrients = NutrientAmounts(12_345.0))), previous = emptyList())
        assertNothingClipped("Heute over target")
    }

    @Test
    fun theAddScreenFits() {
        val foods = listOf(
            testFood(id = 1, name = "Haferflocken zart mit Vollkorn", brand = "Kölln Müsli Manufaktur"),
            testFood(id = 2, name = "Apfelmus", brand = null),
        )
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                AddFoodContent(
                    subtitle = "Frühstück · Heute",
                    query = "",
                    onQueryChange = {},
                    state = AddFoodState(AddMode.CUSTOM, foods, EnergyUnit.KCAL),
                    onSelectFilter = {},
                    onBack = {},
                    onQuickEntry = {},
                    onCreateCustom = {},
                    onLogFood = {},
                    onEditFood = {},
                )
            }
        }
        assertNothingClipped("Lebensmittel hinzufügen")
        foods.forEach { food ->
            compose.scrollListTo(food.name)
            assertNothingClipped("Lebensmittel hinzufügen at ${food.name}")
        }
    }

    @Test
    fun theEmptyAddScreenFits() {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                AddFoodContent(
                    subtitle = "Frühstück · Heute",
                    query = "",
                    onQueryChange = {},
                    state = AddFoodState(AddMode.RECENT, emptyList(), EnergyUnit.KCAL),
                    onSelectFilter = {},
                    onBack = {},
                    onQuickEntry = {},
                    onCreateCustom = {},
                    onLogFood = {},
                    onEditFood = {},
                )
            }
        }
        assertNothingClipped("empty Lebensmittel hinzufügen")
        assertEquals(AddFilter.RECENT, AddFoodState(AddMode.RECENT, emptyList(), EnergyUnit.KCAL).selectedFilter)
    }

    @Test
    fun theQuickEntryFormFits() {
        val draft = QuickEntryDraft(label = "Kaffee mit Milch", kcal = "900", protein = "10", carbs = "30", fat = "10")
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                QuickEntryContent(
                    isEditing = false,
                    isLoaded = true,
                    notFound = false,
                    subtitle = "Frühstück · Heute",
                    draft = draft,
                    evaluation = draft.evaluate(),
                    meal = Meal.FRUEHSTUECK,
                    canSave = true,
                    snackbarHostState = snackbar,
                    onEdit = {},
                    onSelectMeal = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }
        assertNothingClipped("Schnelleintrag (with warning)")
    }

    @Test
    fun theCustomFoodFormFits() {
        val draft = CustomFoodDraft(
            name = "Haferflocken",
            kcal = "1500",
            protein = "13,5",
            carbs = "58,7",
            sugar = "70",
            fat = "7",
            perUnit = PerUnit.MILLILITER,
            barcode = "123",
        )
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                CustomFoodContent(
                    isEditing = true,
                    isLoaded = true,
                    notFound = false,
                    foodName = "Haferflocken",
                    draft = draft,
                    evaluation = draft.evaluate(),
                    canSave = false,
                    snackbarHostState = snackbar,
                    onEdit = {},
                    onSelectPerUnit = {},
                    onSave = {},
                    onDelete = {},
                    onBack = {},
                )
            }
        }
        assertNothingClipped("Eigenes Lebensmittel (with errors and warnings)")
    }

    @Test
    fun theAmountScreenFits() {
        val food = testFood(id = 1, name = "Haferflocken zart mit Vollkorn", brand = "Kölln Müsli Manufaktur", servingG = 40.0)
        val subject = PortionSubject(
            name = food.name,
            brand = food.brand,
            perUnit = food.perUnit,
            per100 = food.nutrients,
            units = AmountUnit.forFood(food.perUnit, food.servingG),
            showTable = true,
        )
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                PortionContent(
                    isEditing = false,
                    isLoaded = true,
                    notFound = false,
                    subtitle = "Frühstück · Heute",
                    subject = subject,
                    amountText = "1",
                    amountError = null,
                    unitIndex = 1,
                    meal = Meal.FRUEHSTUECK,
                    energyUnit = EnergyUnit.KCAL,
                    preview = food.nutrients.forGrams(40.0),
                    canSave = true,
                    snackbarHostState = snackbar,
                    onAmountChange = {},
                    onSelectUnit = {},
                    onSelectMeal = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }
        assertNothingClipped("Menge wählen")
    }
}
