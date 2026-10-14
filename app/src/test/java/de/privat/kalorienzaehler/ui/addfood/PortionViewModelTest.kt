package de.privat.kalorienzaehler.ui.addfood

import app.cash.turbine.test
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodPortion
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.FormError
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeFavoriteRepository
import de.privat.kalorienzaehler.testing.FakeFoodRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.testBls
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testFood
import de.privat.kalorienzaehler.testing.testOff
import de.privat.kalorienzaehler.testing.testQuickEntry
import de.privat.kalorienzaehler.ui.navigation.PortionRoute
import de.privat.kalorienzaehler.ui.navigation.PortionTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PortionViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val foods = FakeCustomFoodRepository()
    private val database = FakeFoodRepository()
    private val diary = FakeDiaryRepository()
    private val favorites = FakeFavoriteRepository()
    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }

    private fun viewModel(target: PortionTarget) = PortionViewModel(
        PortionRoute(target),
        DefaultFoodCatalog(database, foods, favorites, diary),
        favorites,
        diary,
        profiles,
        TEST_CLOCK,
    )

    private fun logging(foodId: Long, meal: Meal = Meal.MITTAGESSEN) =
        logging(FoodKey(FoodSource.CUSTOM, foodId.toString()), meal)

    private fun logging(key: FoodKey, meal: Meal = Meal.MITTAGESSEN) =
        viewModel(PortionTarget.NewFromFood(key.source, key.ref, TEST_TODAY.toEpochDay(), meal))

    private fun editing(entryId: Long) = viewModel(PortionTarget.ExistingEntry(entryId))

    @Test
    fun startsAt100GramsForAFoodWithoutAPortion() {
        val food = foods.preset(testFood(kcal = 372.0)).single()
        val viewModel = logging(food.id)
        assertTrue(viewModel.isLoaded)
        assertEquals("100", viewModel.amountText)
        assertEquals("g", viewModel.selectedUnit?.key)
        assertEquals(372.0, viewModel.preview!!.kcal, 1e-9)
        assertEquals(Meal.MITTAGESSEN, viewModel.meal)
    }

    @Test
    fun startsWithOnePortionWhenThePackStatesOne() {
        val food = foods.preset(testFood(kcal = 400.0, servingG = 30.0)).single()
        val viewModel = logging(food.id)
        assertEquals(listOf("g", "Portion (30 g)"), viewModel.subject?.units?.map { it.key })
        assertEquals("1", viewModel.amountText)
        assertEquals("Portion (30 g)", viewModel.selectedUnit?.key)
        assertEquals(120.0, viewModel.preview!!.kcal, 1e-9)
    }

    @Test
    fun thePreviewFollowsTheAmountLive() {
        val food = foods.preset(testFood(kcal = 372.0)).single()
        val viewModel = logging(food.id)
        viewModel.onAmountChange("150")
        assertEquals(558.0, viewModel.preview!!.kcal, 1e-9)
        assertEquals(20.25, viewModel.preview!!.protein!!, 1e-9)
        viewModel.onAmountChange("37,5")
        assertEquals(139.5, viewModel.preview!!.kcal, 1e-9)
    }

    @Test
    fun switchingTheUnitStartsAtASensibleAmount() {
        val food = foods.preset(testFood(servingG = 30.0)).single()
        val viewModel = logging(food.id)
        viewModel.selectUnit(0)
        assertEquals("100", viewModel.amountText)
        viewModel.selectUnit(1)
        assertEquals("1", viewModel.amountText)
        viewModel.onAmountChange("2")
        viewModel.selectUnit(1) // same unit again: nothing changes
        assertEquals("2", viewModel.amountText)
    }

    @Test
    fun anUnusableAmountHasNoPreviewAndCannotBeSaved() {
        val food = foods.preset(testFood()).single()
        val viewModel = logging(food.id)
        viewModel.onAmountChange("")
        assertNull(viewModel.preview)
        assertFalse(viewModel.canSave)
        assertEquals(FormError.REQUIRED, viewModel.amountError)
        viewModel.onAmountChange("0")
        assertEquals(FormError.OUT_OF_RANGE, viewModel.amountError)
        viewModel.onAmountChange("1x")
        assertEquals(FormError.UNREADABLE, viewModel.amountError)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun loggingAddsAnEntryWithASnapshotOfTheValues() = runTest {
        val food = foods.preset(testFood(name = "Haferflocken", brand = "Kölln", kcal = 372.0)).single()
        val viewModel = logging(food.id, Meal.FRUEHSTUECK)
        viewModel.onAmountChange("50")
        viewModel.selectMeal(Meal.ABENDESSEN)
        viewModel.events.test {
            viewModel.save()
            assertEquals(FormEvent.SAVED, awaitItem())
        }
        val entry = diary.stored.single()
        assertEquals(FoodSource.CUSTOM, entry.source)
        assertEquals(food.id.toString(), entry.foodRef)
        assertEquals("Haferflocken", entry.name)
        assertEquals("Kölln", entry.brand)
        assertEquals(50.0, entry.amount, 0.0)
        assertEquals("g", entry.unit)
        assertEquals(50.0, entry.grams, 0.0)
        assertEquals(186.0, entry.nutrients.kcal, 1e-9)
        assertEquals(Meal.ABENDESSEN, entry.meal)
        assertEquals(TEST_TODAY, entry.date)
        assertEquals(TEST_CLOCK.millis(), entry.createdAtMillis)
    }

    @Test
    fun aPortionEntryRemembersTheGramsBehindIt() = runTest {
        val food = foods.preset(testFood(kcal = 400.0, servingG = 30.0)).single()
        val viewModel = logging(food.id)
        viewModel.onAmountChange("2")
        viewModel.save()
        val entry = diary.stored.single()
        assertEquals("Portion (30 g)", entry.unit)
        assertEquals(2.0, entry.amount, 0.0)
        assertEquals(60.0, entry.grams, 1e-9)
        assertEquals(240.0, entry.nutrients.kcal, 1e-9)
    }

    @Test
    fun unknownMacrosOfAFoodStayUnknownInTheEntry() = runTest {
        val food = foods.preset(
            testFood(kcal = 100.0).copy(nutrients = testFood().nutrients.copy(kcal = 100.0, protein = null, fat = null)),
        ).single()
        val viewModel = logging(food.id)
        viewModel.save()
        val entry = diary.stored.single()
        assertNull(entry.nutrients.protein)
        assertNull(entry.nutrients.fat)
        assertEquals(58.7, entry.nutrients.carbs!!, 1e-9)
    }

    @Test
    fun aFoodThatNoLongerExistsIsReported() {
        val viewModel = logging(404)
        assertTrue(viewModel.notFound)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun aFailedSaveIsReported() = runTest {
        val food = foods.preset(testFood()).single()
        val viewModel = logging(food.id)
        diary.failWrites = true
        viewModel.events.test {
            viewModel.save()
            assertEquals(FormEvent.FAILED, awaitItem())
        }
        assertEquals(emptyList<Any>(), diary.stored)
    }

    @Test
    fun theEnergyUnitComesFromTheProfile() {
        profiles.preset(sampleProfile(energyUnit = EnergyUnit.KJ))
        val food = foods.preset(testFood()).single()
        assertEquals(EnergyUnit.KJ, logging(food.id).energyUnit)
    }

    @Test
    fun milliliterFoodsKeepMilliliters() {
        val food = foods.preset(testFood(name = "Milch", perUnit = PerUnit.MILLILITER, servingG = 250.0)).single()
        val viewModel = logging(food.id)
        assertEquals(listOf("ml", "Portion (250 ml)"), viewModel.subject?.units?.map { it.key })
    }

    // --- Foods of the bundled database ---------------------------------------------------------

    @Test
    fun aBlsFoodOffersItsTypicalPortionsAndMarksEstimatedOnes() {
        val apple = testBls("F100000", "Apfel", kcal = 52.0)
        database.preset(apple)
        database.presetPortions(
            apple,
            FoodPortion("Stück (mittel)", 150.0, estimated = true),
            FoodPortion("Scheibe", 20.0, estimated = false),
        )
        val viewModel = logging(apple.key)
        val units = viewModel.subject!!.units
        assertEquals(listOf("g", "Stück (mittel)", "Scheibe"), units.map { it.key })
        assertEquals(listOf(false, true, false), units.map { it.estimated })
        // Starts at the first portion that is not a guess; 100 g would be the fallback.
        assertEquals("Scheibe", viewModel.selectedUnit?.key)
        assertEquals("1", viewModel.amountText)
        assertEquals(10.4, viewModel.preview!!.kcal, 1e-9)
        viewModel.selectUnit(1)
        assertTrue(viewModel.selectedUnit!!.estimated)
        assertEquals(78.0, viewModel.preview!!.kcal, 1e-9)
    }

    @Test
    fun aBlsFoodWithOnlyEstimatedPortionsStartsAt100Grams() {
        val apple = testBls("F100000", "Apfel", kcal = 52.0)
        database.preset(apple)
        database.presetPortions(apple, FoodPortion("Stück (mittel)", 150.0, estimated = true))
        val viewModel = logging(apple.key)
        assertEquals("g", viewModel.selectedUnit?.key)
        assertEquals("100", viewModel.amountText)
    }

    @Test
    fun loggingABlsFoodStoresItsKeyAndTheEstimatedWeight() = runTest {
        val apple = testBls("F100000", "Apfel", kcal = 52.0)
        database.preset(apple)
        database.presetPortions(apple, FoodPortion("Stück (mittel)", 150.0, estimated = true))
        val viewModel = logging(apple.key)
        viewModel.selectUnit(1)
        viewModel.onAmountChange("2")
        viewModel.save()
        val entry = diary.stored.single()
        assertEquals(FoodSource.BLS, entry.source)
        assertEquals("F100000", entry.foodRef)
        assertEquals("Stück (mittel)", entry.unit)
        assertEquals(2.0, entry.amount, 0.0)
        assertEquals(300.0, entry.grams, 1e-9)
        assertEquals(156.0, entry.nutrients.kcal, 1e-9)
    }

    @Test
    fun anOpenFoodFactsFoodUsesItsPackPortion() {
        val bar = testOff("4000000000001", "Müsliriegel", "Marke", kcal = 400.0)
            .copy(servingG = 25.0, servingLabel = "1 Riegel")
        database.preset(bar)
        val viewModel = logging(bar.key)
        assertEquals(listOf("g", "Portion (25 g)"), viewModel.subject?.units?.map { it.key })
        assertEquals("Marke", viewModel.subject?.brand)
        assertEquals(100.0, viewModel.preview!!.kcal, 1e-9)
        assertTrue(viewModel.subject!!.showTable)
    }

    @Test
    fun aDatabaseFoodThatIsGoneIsReported() {
        val viewModel = logging(FoodKey(FoodSource.OFF, "4999999999999"))
        assertTrue(viewModel.notFound)
        assertFalse(viewModel.canSave)
    }

    // --- Favorites -----------------------------------------------------------------------------

    @Test
    fun theStarTogglesTheFavorite() = runTest {
        val apple = testBls("F100000", "Apfel")
        database.preset(apple)
        val viewModel = logging(apple.key)
        assertEquals(false, viewModel.isFavorite)
        viewModel.toggleFavorite()
        assertEquals(true, viewModel.isFavorite)
        assertEquals(setOf(apple.key), favorites.stored)
        viewModel.toggleFavorite()
        assertEquals(false, viewModel.isFavorite)
        assertEquals(emptySet<FoodKey>(), favorites.stored)
    }

    @Test
    fun aFavoriteIsShownAsStarredWhenOpened() {
        val food = foods.preset(testFood()).single()
        favorites.preset(FoodKey(FoodSource.CUSTOM, food.id.toString()))
        assertEquals(true, logging(food.id).isFavorite)
    }

    @Test
    fun anOwnFoodCanBeStarred() = runTest {
        val food = foods.preset(testFood()).single()
        val viewModel = logging(food.id)
        viewModel.toggleFavorite()
        assertEquals(setOf(FoodKey(FoodSource.CUSTOM, food.id.toString())), favorites.stored)
    }

    @Test
    fun anEntryOfAStarredFoodShowsTheStarToo() {
        val apple = testBls("F100000", "Apfel")
        database.preset(apple)
        favorites.preset(apple.key)
        val entry = diary.preset(testEntry().copy(source = FoodSource.BLS, foodRef = "F100000", name = "Apfel")).single()
        assertEquals(true, editing(entry.id).isFavorite)
    }

    @Test
    fun editingAnEntryKeepsTheEstimateFlagOfItsPortion() {
        val apple = testBls("F100000", "Apfel", kcal = 52.0)
        database.preset(apple)
        database.presetPortions(apple, FoodPortion("Stück (mittel)", 150.0, estimated = true))
        val entry = diary.preset(
            testEntry().copy(source = FoodSource.BLS, foodRef = "F100000", name = "Apfel", amount = 2.0, unit = "Stück (mittel)", grams = 300.0),
        ).single()
        val viewModel = editing(entry.id)
        assertTrue(viewModel.selectedUnit!!.estimated)
    }

    @Test
    fun aQuickEntryHasNoStar() {
        val entry = diary.preset(testQuickEntry()).single()
        assertNull(editing(entry.id).isFavorite)
    }

    @Test
    fun aFavoriteThatCannotBeStoredIsReported() = runTest {
        val food = foods.preset(testFood()).single()
        val viewModel = logging(food.id)
        val failing = object : de.privat.kalorienzaehler.data.repository.FavoriteRepository by favorites {
            override suspend fun setFavorite(key: FoodKey, favorite: Boolean) = throw java.io.IOException("disk full")
        }
        val failingViewModel = PortionViewModel(
            PortionRoute(PortionTarget.NewFromFood(FoodSource.CUSTOM, food.id.toString(), TEST_TODAY.toEpochDay(), Meal.SNACKS)),
            DefaultFoodCatalog(database, foods, favorites, diary),
            failing,
            diary,
            profiles,
            TEST_CLOCK,
        )
        failingViewModel.events.test {
            failingViewModel.toggleFavorite()
            assertEquals(FormEvent.FAILED, awaitItem())
        }
        assertEquals(false, viewModel.isFavorite)
    }

    @Test
    fun editingShowsTheStoredAmountAndKeepsItsUnit() {
        val entry = diary.preset(testEntry(grams = 150.0, meal = Meal.ABENDESSEN)).single()
        val viewModel = editing(entry.id)
        assertTrue(viewModel.isEditing)
        assertEquals("150", viewModel.amountText)
        assertEquals(listOf("g"), viewModel.subject?.units?.map { it.key })
        assertEquals(Meal.ABENDESSEN, viewModel.meal)
        assertEquals(false, viewModel.subject?.showTable)
        assertEquals(300.0, viewModel.preview!!.kcal, 1e-9)
    }

    @Test
    fun editingTheAmountRescalesTheEntryInPlace() = runTest {
        val entry = diary.preset(testEntry(grams = 150.0, createdAtMillis = 55)).single()
        val viewModel = editing(entry.id)
        viewModel.onAmountChange("300")
        assertEquals(600.0, viewModel.preview!!.kcal, 1e-9)
        viewModel.selectMeal(Meal.SNACKS)
        viewModel.events.test {
            viewModel.save()
            assertEquals(FormEvent.SAVED, awaitItem())
        }
        val changed = diary.stored.single()
        assertEquals(entry.id, changed.id)
        assertEquals(300.0, changed.amount, 1e-9)
        assertEquals(300.0, changed.grams, 1e-9)
        assertEquals(600.0, changed.nutrients.kcal, 1e-9)
        assertEquals(30.0, changed.nutrients.protein!!, 1e-9)
        assertEquals(Meal.SNACKS, changed.meal)
        assertEquals(55, changed.createdAtMillis)
        assertEquals(entry.name, changed.name)
    }

    @Test
    fun editingAPortionEntryCountsPortions() = runTest {
        val entry = diary.preset(
            testEntry().copy(amount = 2.0, unit = "Portion (30 g)", grams = 60.0),
        ).single()
        val viewModel = editing(entry.id)
        assertEquals("2", viewModel.amountText)
        viewModel.onAmountChange("3")
        viewModel.save()
        val changed = diary.stored.single()
        assertEquals(3.0, changed.amount, 0.0)
        assertEquals(90.0, changed.grams, 1e-9)
        assertEquals("Portion (30 g)", changed.unit)
    }

    @Test
    fun anEntryThatWasDeletedIsReported() {
        assertTrue(editing(404).notFound)
    }

    @Test
    fun quickEntriesHaveNoAmountToEdit() {
        val entry = diary.preset(testQuickEntry()).single()
        assertTrue(editing(entry.id).notFound)
    }
}
