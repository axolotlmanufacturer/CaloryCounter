package de.privat.kalorienzaehler.ui.addfood

import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.FormError
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testFood
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
    private val diary = FakeDiaryRepository()
    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }

    private fun logging(foodId: Long, meal: Meal = Meal.MITTAGESSEN) = PortionViewModel(
        PortionRoute(PortionTarget.NewFromCustomFood(foodId, TEST_TODAY.toEpochDay(), meal)),
        foods,
        diary,
        profiles,
        TEST_CLOCK,
    )

    private fun editing(entryId: Long) = PortionViewModel(
        PortionRoute(PortionTarget.ExistingEntry(entryId)),
        foods,
        diary,
        profiles,
        TEST_CLOCK,
    )

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
