package de.privat.kalorienzaehler.ui.addfood

import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testQuickEntry
import de.privat.kalorienzaehler.ui.navigation.QuickEntryRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickEntryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val diary = FakeDiaryRepository()

    private fun newEntry(meal: Meal = Meal.SNACKS) =
        QuickEntryViewModel(QuickEntryRoute(TEST_TODAY.toEpochDay(), meal), diary, TEST_CLOCK)

    private fun editing(id: Long) =
        QuickEntryViewModel(QuickEntryRoute(TEST_TODAY.toEpochDay(), Meal.SNACKS, entryId = id), diary, TEST_CLOCK)

    @Test
    fun cannotBeSavedWithoutCalories() {
        val viewModel = newEntry()
        assertFalse(viewModel.canSave)
        viewModel.edit { it.copy(label = "Kaffee") }
        assertFalse(viewModel.canSave)
        viewModel.edit { it.copy(kcal = "30") }
        assertTrue(viewModel.canSave)
    }

    @Test
    fun savesAQuickEntryForTheDayAndMealItWasOpenedFor() = runTest {
        val viewModel = newEntry(Meal.FRUEHSTUECK)
        viewModel.edit { it.copy(label = "Kaffee mit Milch", kcal = "42,5", protein = "2", carbs = "3,5") }
        viewModel.events.test {
            viewModel.save(defaultLabel = "Schnelleintrag")
            assertEquals(FormEvent.SAVED, awaitItem())
        }
        val saved = diary.stored.single()
        assertEquals(FoodSource.QUICK, saved.source)
        assertEquals("Kaffee mit Milch", saved.name)
        assertEquals(Meal.FRUEHSTUECK, saved.meal)
        assertEquals(TEST_TODAY, saved.date)
        assertEquals(NutrientAmounts(42.5, protein = 2.0, carbs = 3.5, fat = null), saved.nutrients)
        assertNull(saved.foodRef)
        assertEquals(TEST_CLOCK.millis(), saved.createdAtMillis)
    }

    @Test
    fun withoutALabelTheDefaultLabelIsUsedAndUnknownMacrosStayUnknown() = runTest {
        val viewModel = newEntry()
        viewModel.edit { it.copy(kcal = "250") }
        viewModel.save(defaultLabel = "Schnelleintrag")
        val saved = diary.stored.single()
        assertEquals("Schnelleintrag", saved.name)
        assertNull(saved.nutrients.protein)
        assertNull(saved.nutrients.carbs)
        assertNull(saved.nutrients.fat)
    }

    @Test
    fun theMealCanBeChosen() = runTest {
        val viewModel = newEntry(Meal.SNACKS)
        viewModel.selectMeal(Meal.ABENDESSEN)
        viewModel.edit { it.copy(kcal = "100") }
        viewModel.save("Schnelleintrag")
        assertEquals(Meal.ABENDESSEN, diary.stored.single().meal)
    }

    @Test
    fun anInvalidFormSavesNothing() = runTest {
        val viewModel = newEntry()
        viewModel.edit { it.copy(kcal = "abc") }
        viewModel.save("Schnelleintrag")
        assertEquals(emptyList<Any>(), diary.stored)
    }

    @Test
    fun aFailedSaveIsReportedAndCanBeRetried() = runTest {
        val viewModel = newEntry()
        viewModel.edit { it.copy(kcal = "100") }
        diary.failWrites = true
        viewModel.events.test {
            viewModel.save("Schnelleintrag")
            assertEquals(FormEvent.FAILED, awaitItem())
            assertTrue(viewModel.canSave)
            diary.failWrites = false
            viewModel.save("Schnelleintrag")
            assertEquals(FormEvent.SAVED, awaitItem())
        }
        assertEquals(1, diary.stored.size)
    }

    @Test
    fun editingStartsFromTheStoredEntry() {
        val stored = diary.preset(
            testQuickEntry(meal = Meal.MITTAGESSEN, name = "Imbiss", nutrients = NutrientAmounts(320.0, protein = 12.0)),
        ).single()
        val viewModel = editing(stored.id)
        assertTrue(viewModel.isEditing)
        assertTrue(viewModel.isLoaded)
        assertEquals("Imbiss", viewModel.draft.label)
        assertEquals("320", viewModel.draft.kcal)
        assertEquals("12", viewModel.draft.protein)
        assertEquals("", viewModel.draft.carbs)
        assertEquals(Meal.MITTAGESSEN, viewModel.meal)
    }

    @Test
    fun editingChangesTheEntryInPlace() = runTest {
        val stored = diary.preset(testQuickEntry(meal = Meal.MITTAGESSEN, name = "Imbiss", createdAtMillis = 77)).single()
        val viewModel = editing(stored.id)
        viewModel.edit { it.copy(label = "Größerer Imbiss", kcal = "400") }
        viewModel.selectMeal(Meal.ABENDESSEN)
        viewModel.save("Schnelleintrag")
        val changed = diary.stored.single()
        assertEquals(stored.id, changed.id)
        assertEquals("Größerer Imbiss", changed.name)
        assertEquals(400.0, changed.nutrients.kcal, 0.0)
        assertEquals(Meal.ABENDESSEN, changed.meal)
        assertEquals(77, changed.createdAtMillis)
        assertEquals(stored.date, changed.date)
    }

    @Test
    fun anEntryThatNoLongerExistsIsReported() {
        val viewModel = editing(999)
        assertTrue(viewModel.notFound)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun onlyQuickEntriesCanBeEditedHere() {
        val food = diary.preset(testEntry()).single()
        assertTrue(editing(food.id).notFound)
    }
}
