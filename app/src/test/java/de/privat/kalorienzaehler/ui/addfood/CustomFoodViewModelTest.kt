package de.privat.kalorienzaehler.ui.addfood

import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.diary.FoodWarning
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.testFood
import de.privat.kalorienzaehler.ui.navigation.CustomFoodRoute
import de.privat.kalorienzaehler.ui.navigation.LogTarget
import de.privat.kalorienzaehler.domain.diary.Meal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CustomFoodViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val foods = FakeCustomFoodRepository()

    private fun creating() = CustomFoodViewModel(CustomFoodRoute(logTo = LogTarget(0, Meal.SNACKS)), foods, TEST_CLOCK)

    private fun editing(id: Long) = CustomFoodViewModel(CustomFoodRoute(customFoodId = id), foods, TEST_CLOCK)

    @Test
    fun aNewFoodNeedsANameAndCalories() {
        val viewModel = creating()
        assertFalse(viewModel.canSave)
        viewModel.edit { it.copy(name = "Haferflocken") }
        assertFalse(viewModel.canSave)
        viewModel.edit { it.copy(kcal = "372") }
        assertTrue(viewModel.canSave)
    }

    @Test
    fun savesTheFoodWithTheValuesAsPrinted() = runTest {
        val viewModel = creating()
        viewModel.edit {
            it.copy(
                name = "Haferflocken", brand = "Kölln", barcode = "4006381333931", kcal = "372",
                protein = "13,5", carbs = "58,7", sugar = "1,1", fat = "7", fiber = "10", salt = "0,02", servingG = "40",
            )
        }
        viewModel.events.test {
            viewModel.save()
            val saved = awaitItem() as CustomFoodEvent.Saved
            assertTrue(saved.isNew)
            val stored = foods.stored.single()
            assertEquals(saved.foodId, stored.id)
            assertEquals("Haferflocken", stored.name)
            assertEquals("Kölln", stored.brand)
            assertEquals("4006381333931", stored.barcode)
            assertEquals(372.0, stored.nutrients.kcal, 0.0)
            assertEquals(13.5, stored.nutrients.protein!!, 0.0)
            assertEquals(0.02, stored.nutrients.salt!!, 0.0)
            assertEquals(40.0, stored.servingG)
            assertEquals(PerUnit.GRAM, stored.perUnit)
            assertEquals(TEST_CLOCK.millis(), stored.createdAtMillis)
        }
    }

    @Test
    fun leftOutValuesStayUnknown() = runTest {
        val viewModel = creating()
        viewModel.edit { it.copy(name = "Wasser", kcal = "0") }
        viewModel.selectPerUnit(PerUnit.MILLILITER)
        viewModel.save()
        val stored = foods.stored.single()
        assertEquals(PerUnit.MILLILITER, stored.perUnit)
        assertNull(stored.nutrients.protein)
        assertNull(stored.nutrients.fat)
        assertNull(stored.servingG)
        assertNull(stored.barcode)
        assertNull(stored.brand)
    }

    @Test
    fun doesNotBlockButWarnsWhenTheValuesDoNotAddUp() {
        val viewModel = creating()
        viewModel.edit { it.copy(name = "Komisch", kcal = "800", protein = "10", carbs = "10", fat = "1") }
        assertTrue(viewModel.canSave)
        assertTrue(viewModel.evaluation.warnings.single() is FoodWarning.KcalMacroMismatch)
    }

    @Test
    fun editingStartsFromTheStoredFood() {
        val stored = foods.preset(testFood(name = "Müsli", brand = "Alnatura", kcal = 1234.5, servingG = 45.0)).single()
        val viewModel = editing(stored.id)
        assertTrue(viewModel.isLoaded)
        assertEquals("Müsli", viewModel.draft.name)
        assertEquals("Alnatura", viewModel.draft.brand)
        // No thousands dot: the text has to parse back.
        assertEquals("1234,5", viewModel.draft.kcal)
        assertEquals("45", viewModel.draft.servingG)
        assertEquals("Müsli", viewModel.storedName)
    }

    @Test
    fun editingUpdatesTheSameFood() = runTest {
        val stored = foods.preset(testFood(name = "Müsli")).single()
        val viewModel = editing(stored.id)
        viewModel.edit { it.copy(name = "Müsli knusprig", kcal = "400") }
        viewModel.events.test {
            viewModel.save()
            val saved = awaitItem() as CustomFoodEvent.Saved
            assertFalse(saved.isNew)
            assertEquals(stored.id, saved.foodId)
        }
        val changed = foods.stored.single()
        assertEquals("Müsli knusprig", changed.name)
        assertEquals(400.0, changed.nutrients.kcal, 0.0)
        assertEquals(stored.createdAtMillis, changed.createdAtMillis)
        assertEquals(TEST_CLOCK.millis(), changed.updatedAtMillis)
    }

    @Test
    fun aFoodThatNoLongerExistsIsReported() {
        val viewModel = editing(404)
        assertTrue(viewModel.notFound)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun deletingRemovesTheFood() = runTest {
        val stored = foods.preset(testFood(name = "Weg damit")).single()
        val viewModel = editing(stored.id)
        viewModel.events.test {
            viewModel.delete()
            assertEquals(CustomFoodEvent.Deleted, awaitItem())
        }
        assertEquals(emptyList<Any>(), foods.stored)
    }

    @Test
    fun aFailedSaveIsReported() = runTest {
        val viewModel = creating()
        viewModel.edit { it.copy(name = "Haferflocken", kcal = "372") }
        foods.failWrites = true
        viewModel.events.test {
            viewModel.save()
            assertEquals(CustomFoodEvent.Failed, awaitItem())
        }
        assertEquals(emptyList<Any>(), foods.stored)
        assertTrue(viewModel.canSave)
    }
}
