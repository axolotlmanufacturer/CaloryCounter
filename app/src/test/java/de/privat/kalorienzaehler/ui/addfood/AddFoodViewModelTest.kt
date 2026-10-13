package de.privat.kalorienzaehler.ui.addfood

import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.testFood
import de.privat.kalorienzaehler.ui.navigation.AddFoodRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AddFoodViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val foods = FakeCustomFoodRepository()
    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }

    private fun viewModel() = AddFoodViewModel(
        AddFoodRoute(TEST_TODAY.toEpochDay(), Meal.MITTAGESSEN),
        foods,
        profiles,
        TEST_CLOCK,
    )

    private suspend fun AddFoodViewModel.current(): AddFoodState = state.first { it != null }!!

    @Test
    fun knowsWhichDayAndMealItIsFor() {
        val viewModel = viewModel()
        assertEquals(TEST_TODAY, viewModel.date)
        assertEquals(Meal.MITTAGESSEN, viewModel.meal)
        assertEquals(TEST_TODAY, viewModel.today)
    }

    @Test
    fun startsWithTheRecentlyUsedFoods() = runTest {
        val (oats, apple) = foods.preset(testFood(name = "Haferflocken"), testFood(name = "Apfelmus", brand = null))
        foods.presetRecent(apple.id)
        val state = viewModel().current()
        assertEquals(AddMode.RECENT, state.mode)
        assertEquals(AddFilter.RECENT, state.selectedFilter)
        assertEquals(listOf(apple.id), state.foods.map { it.id })
        assertEquals(setOf(oats.id, apple.id), foods.stored.map { it.id }.toSet())
    }

    @Test
    fun withoutRecentsButWithOwnFoodsTheOwnFoodsAreShownFirst() = runTest {
        foods.preset(testFood(name = "Haferflocken"), testFood(name = "Apfelmus", brand = null))
        val state = viewModel().current()
        assertEquals(AddMode.CUSTOM, state.mode)
        assertEquals(listOf("Apfelmus", "Haferflocken"), state.foods.map { it.name })
    }

    @Test
    fun withNothingAtAllTheRecentListIsEmpty() = runTest {
        val state = viewModel().current()
        assertEquals(AddMode.RECENT, state.mode)
        assertEquals(emptyList<Any>(), state.foods)
    }

    @Test
    fun theChipsSwitchTheList() = runTest {
        val (_, apple) = foods.preset(testFood(name = "Haferflocken"), testFood(name = "Apfelmus", brand = null))
        foods.presetRecent(apple.id)
        val viewModel = viewModel()
        viewModel.state.test {
            // The state starts out null, and may already be filled by the time the first item is read.
            var first = awaitItem()
            if (first == null) first = awaitItem()
            assertEquals(AddMode.RECENT, first?.mode)
            viewModel.selectFilter(AddFilter.CUSTOM)
            val custom = awaitItem()
            assertEquals(AddMode.CUSTOM, custom?.mode)
            assertEquals(2, custom?.foods?.size)
            viewModel.selectFilter(AddFilter.RECENT)
            assertEquals(AddMode.RECENT, awaitItem()?.mode)
        }
    }

    @Test
    fun typingSearchesTheOwnFoodsIgnoringUmlautsAndCase() = runTest {
        foods.preset(
            testFood(name = "Käsebrot", brand = null),
            testFood(name = "Müsli", brand = "Kölln"),
            testFood(name = "Apfelmus", brand = null),
        )
        val viewModel = viewModel()
        viewModel.onQueryChange("kase")
        assertEquals(listOf("Käsebrot"), viewModel.current().foods.map { it.name })
        viewModel.onQueryChange("KAESE")
        assertEquals(listOf("Käsebrot"), viewModel.current().foods.map { it.name })
        viewModel.onQueryChange("koelln")
        assertEquals(listOf("Müsli"), viewModel.current().foods.map { it.name })
        assertEquals(AddMode.SEARCH, viewModel.current().mode)
        assertNull(viewModel.current().selectedFilter)
    }

    @Test
    fun aBarcodeFindsItsFood() = runTest {
        foods.preset(testFood(name = "Mit Code", barcode = "4006381333931"), testFood(name = "Ohne Code"))
        val viewModel = viewModel()
        viewModel.onQueryChange("4006381")
        assertEquals(listOf("Mit Code"), viewModel.current().foods.map { it.name })
    }

    @Test
    fun everyWordOfTheQueryHasToMatch() = runTest {
        foods.preset(testFood(name = "Haferflocken", brand = "Kölln"), testFood(name = "Haferdrink", brand = "Alpro"))
        val viewModel = viewModel()
        viewModel.onQueryChange("hafer koel")
        assertEquals(listOf("Haferflocken"), viewModel.current().foods.map { it.name })
    }

    @Test
    fun nothingFoundIsAnEmptySearchResult() = runTest {
        foods.preset(testFood(name = "Haferflocken"))
        val viewModel = viewModel()
        viewModel.onQueryChange("xyz")
        val state = viewModel.current()
        assertEquals(AddMode.SEARCH, state.mode)
        assertEquals(emptyList<Any>(), state.foods)
    }

    @Test
    fun clearingTheSearchGoesBackToTheChosenList() = runTest {
        foods.preset(testFood(name = "Haferflocken"))
        val viewModel = viewModel()
        viewModel.onQueryChange("hafer")
        assertEquals(AddMode.SEARCH, viewModel.current().mode)
        viewModel.onQueryChange("")
        assertEquals(AddMode.CUSTOM, viewModel.current().mode)
        assertEquals("", viewModel.query)
    }

    @Test
    fun theSearchFieldShowsExactlyWhatWasTyped() {
        val viewModel = viewModel()
        viewModel.onQueryChange("Käs")
        assertEquals("Käs", viewModel.query)
    }

    @Test
    fun theEnergyUnitComesFromTheProfile() = runTest {
        profiles.preset(sampleProfile(energyUnit = EnergyUnit.KJ))
        assertEquals(EnergyUnit.KJ, viewModel().current().energyUnit)
    }
}
