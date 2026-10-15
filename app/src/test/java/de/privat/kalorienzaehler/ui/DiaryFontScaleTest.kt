package de.privat.kalorienzaehler.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.domain.diary.CustomFoodDraft
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.FoodHit
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
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
import de.privat.kalorienzaehler.testing.textsWithBrokenWords
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

    private fun assertNothingClipped(label: String) {
        assertEquals("clipped texts on $label", emptyList<String>(), compose.clippedTexts())
        assertEquals("words broken across lines on $label", emptyList<String>(), compose.textsWithBrokenWords())
    }

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
    fun theDateBarFitsOnOtherDaysToo() {
        heute(emptyList(), previous = emptyList())
        listOf(
            string(R.string.today_previous_day),
            string(R.string.today_previous_day),
            string(R.string.today_previous_day),
        ).forEach {
            compose.onNodeWithContentDescription(it).performClick()
            assertNothingClipped("the date bar")
        }
        // Three days back (Saturday, 10 October 2026) with the long weekday date and the "to today" icon.
        compose.onNodeWithText("Sa., 10.10.2026").assertIsDisplayed()
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

    private fun addScreen(state: AddFoodState, query: String = "") {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                AddFoodContent(
                    subtitle = "Frühstück · Heute",
                    query = query,
                    onQueryChange = {},
                    state = state,
                    onSelectFilter = {},
                    onBack = {},
                    onQuickEntry = {},
                    onCreateCustom = {},
                    onScan = {},
                    onLogFood = {},
                    onEditFood = {},
                )
            }
        }
    }

    private fun hit(source: FoodSource, ref: String, name: String, brand: String?, kcal: Double = 372.0, perUnit: PerUnit = PerUnit.GRAM) =
        FoodHit(FoodKey(source, ref), name, brand, kcal, perUnit)

    @Test
    fun theAddScreenFits() {
        val foods = listOf(
            hit(FoodSource.CUSTOM, "1", "Haferflocken zart mit Vollkorn", "Kölln Müsli Manufaktur"),
            hit(FoodSource.BLS, "M110100", "Käse, Gouda, mindestens 48 % Fett i. Tr., Vollfettstufe", null),
            hit(FoodSource.OFF, "4008400154329", "Bio Haferdrink ohne Zuckerzusatz", "Alnatura Naturkost", 42.0, PerUnit.MILLILITER),
        )
        addScreen(AddFoodState(AddMode.SEARCH, foods, EnergyUnit.KCAL, FoodDbStatus.Ready), query = "hafer")
        assertNothingClipped("Lebensmittel hinzufügen")
        foods.forEach { food ->
            compose.scrollListTo(food.name)
            assertNothingClipped("Lebensmittel hinzufügen at ${food.name}")
        }
    }

    @Test
    fun theAddScreenFitsInKilojoules() {
        val foods = listOf(hit(FoodSource.OFF, "4008400154329", "Bio Haferdrink ohne Zuckerzusatz", "Alnatura Naturkost", 42.0))
        addScreen(AddFoodState(AddMode.FAVORITES, foods, EnergyUnit.KJ, FoodDbStatus.Ready))
        assertNothingClipped("Lebensmittel hinzufügen (kJ)")
    }

    @Test
    fun theEmptyAddScreenFits() {
        addScreen(AddFoodState(AddMode.RECENT, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready))
        showsHint(R.string.add_empty_recent, "empty Lebensmittel hinzufügen")
        assertEquals(AddFilter.RECENT, AddFoodState(AddMode.RECENT, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready).selectedFilter)
    }

    @Test
    fun theEmptyFavoritesHintFits() {
        addScreen(AddFoodState(AddMode.FAVORITES, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready))
        showsHint(R.string.add_empty_favorites, "empty Favoriten")
    }

    @Test
    fun thePreparingDatabaseHintFits() {
        addScreen(AddFoodState(AddMode.SEARCH, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Preparing), query = "hafer")
        showsHint(R.string.add_db_preparing, "Datenbank wird vorbereitet")
    }

    @Test
    fun theFailedDatabaseHintFits() {
        addScreen(AddFoodState(AddMode.SEARCH, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Failed), query = "hafer")
        showsHint(R.string.add_db_failed, "Datenbank nicht verfügbar")
    }

    @Test
    fun theTooShortSearchHintAndNoResultsFit() {
        addScreen(AddFoodState(AddMode.SEARCH, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready, queryTooShort = true), query = "h")
        showsHint(R.string.add_query_too_short, "Suche zu kurz")
    }

    @Test
    fun noResultsFit() {
        addScreen(AddFoodState(AddMode.SEARCH, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready), query = "xyz")
        showsHint(R.string.add_no_results, "keine Treffer")
    }

    @Test
    fun whileASearchRunsNeitherAnEmptyHintNorStaleResultsAreShown() {
        addScreen(AddFoodState(AddMode.SEARCH, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready, searching = true), query = "hafer")
        compose.onNodeWithText(string(R.string.add_no_results)).assertDoesNotExist()
    }

    /** The hint is on screen (scrolled into view if the list is long at 200 %) and not clipped. */
    private fun showsHint(hint: Int, label: String) {
        compose.scrollListTo(string(hint))
        compose.onNodeWithText(string(hint)).assertIsDisplayed()
        assertNothingClipped(label)
    }

    @Test
    fun theFilterChipsAreOneRadioLikeGroup() {
        addScreen(AddFoodState(AddMode.FAVORITES, emptyList(), EnergyUnit.KCAL, FoodDbStatus.Ready))
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        listOf(R.string.add_chip_recent, R.string.add_chip_favorites, R.string.add_chip_custom).forEach { chip ->
            compose.onNode(hasText(string(chip)) and radio).assertIsDisplayed()
        }
        compose.onNode(hasText(string(R.string.add_chip_favorites))).assertIsSelected()
        compose.onNode(hasText(string(R.string.add_chip_recent))).assertIsNotSelected()
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

    private fun amountScreen(
        subject: PortionSubject,
        unitIndex: Int,
        amountText: String,
        grams: Double,
        isFavorite: Boolean? = false,
        energyUnit: EnergyUnit = EnergyUnit.KCAL,
    ) {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                PortionContent(
                    isEditing = false,
                    isLoaded = true,
                    notFound = false,
                    subtitle = "Frühstück · Heute",
                    subject = subject,
                    amountText = amountText,
                    amountError = null,
                    unitIndex = unitIndex,
                    meal = Meal.FRUEHSTUECK,
                    energyUnit = energyUnit,
                    preview = subject.per100.forGrams(grams),
                    isFavorite = isFavorite,
                    canSave = true,
                    snackbarHostState = snackbar,
                    onAmountChange = {},
                    onSelectUnit = {},
                    onSelectMeal = {},
                    onToggleFavorite = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun theAmountScreenFits() {
        val food = testFood(id = 1, name = "Haferflocken zart mit Vollkorn", brand = "Kölln Müsli Manufaktur", servingG = 40.0)
        val subject = PortionSubject(
            key = FoodKey(FoodSource.CUSTOM, "1"),
            name = food.name,
            brand = food.brand,
            perUnit = food.perUnit,
            per100 = food.nutrients,
            units = AmountUnit.forFood(food.perUnit, food.servingG),
            showTable = true,
        )
        amountScreen(subject, unitIndex = 1, amountText = "1", grams = 40.0)
        assertNothingClipped("Menge wählen")
    }

    @Test
    fun theAmountScreenWithEstimatedPortionsFits() {
        val food = testFood(id = 1, name = "Apfel, roh, mit Schale und Kerngehäuse", brand = null)
        val subject = PortionSubject(
            key = FoodKey(FoodSource.BLS, "F100000"),
            name = food.name,
            brand = null,
            perUnit = PerUnit.GRAM,
            per100 = food.nutrients,
            units = listOf(
                AmountUnit("g", 1.0),
                AmountUnit("Stück (mittel)", 150.0, estimated = true),
                AmountUnit("Stück (klein)", 100.0, estimated = true),
                AmountUnit("Scheibe", 20.0),
            ),
            showTable = true,
        )
        amountScreen(subject, unitIndex = 1, amountText = "2", grams = 300.0, isFavorite = true, energyUnit = EnergyUnit.KJ)
        assertNothingClipped("Menge wählen (geschätzte Portion, Favorit)")
        compose.onNodeWithContentDescription(string(R.string.favorite_remove)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portion_estimated_hint)).performScrollTo().assertIsDisplayed()
        assertNothingClipped("Menge wählen (Schätzwert-Hinweis)")
    }

    @Test
    fun theAmountScreenForAnEntryWithoutAFoodHasNoStar() {
        val food = testFood(id = 1)
        val subject = PortionSubject(
            key = null,
            name = "Schnelleintrag",
            brand = null,
            perUnit = PerUnit.GRAM,
            per100 = food.nutrients,
            units = listOf(AmountUnit("g", 1.0)),
            showTable = false,
        )
        amountScreen(subject, unitIndex = 0, amountText = "100", grams = 100.0, isFavorite = null)
        compose.onNodeWithContentDescription(string(R.string.favorite_add)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.favorite_remove)).assertDoesNotExist()
    }
}
