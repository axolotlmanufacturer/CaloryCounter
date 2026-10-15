package de.privat.kalorienzaehler.ui.addfood

import app.cash.turbine.test
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
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
import de.privat.kalorienzaehler.ui.navigation.AddFoodRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddFoodViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val custom = FakeCustomFoodRepository()
    private val diary = FakeDiaryRepository()
    private val favorites = FakeFavoriteRepository()
    private val database = FakeFoodRepository()
    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }

    private fun viewModel() = AddFoodViewModel(
        AddFoodRoute(TEST_TODAY.toEpochDay(), Meal.MITTAGESSEN),
        DefaultFoodCatalog(database, custom, favorites, diary),
        profiles,
        TEST_CLOCK,
    )

    private suspend fun AddFoodViewModel.current(): AddFoodState = state.first { it != null }!!

    /** Types [text] and waits for its result (the search starts after a short pause in typing). */
    private suspend fun AddFoodViewModel.search(text: String): AddFoodState {
        onQueryChange(text)
        return state.first { it?.mode == AddMode.SEARCH && !it.searching }!!
    }

    @Test
    fun knowsWhichDayAndMealItIsFor() {
        val viewModel = viewModel()
        assertEquals(TEST_TODAY, viewModel.date)
        assertEquals(Meal.MITTAGESSEN, viewModel.meal)
        assertEquals(TEST_TODAY, viewModel.today)
    }

    @Test
    fun opensTheFoodDatabaseRightAway() {
        viewModel()
        assertEquals(1, database.prepareCalls)
    }

    @Test
    fun startsWithTheRecentlyLoggedFoodsOfAnySource() = runTest {
        val (oats) = custom.preset(testFood(name = "Haferflocken"))
        val gouda = testBls("M110100", "Käse, Gouda")
        database.preset(gouda, testBls("B100000", "Brot, Roggen"))
        diary.preset(
            testEntry(name = "Haferflocken").copy(foodRef = oats.id.toString()),
            testEntry(name = "Käse, Gouda").copy(source = FoodSource.BLS, foodRef = "M110100", createdAtMillis = 5),
            testEntry(name = "Käse, Gouda").copy(source = FoodSource.BLS, foodRef = "M110100", createdAtMillis = 6),
        )
        val state = viewModel().current()
        assertEquals(AddMode.RECENT, state.mode)
        assertEquals(AddFilter.RECENT, state.selectedFilter)
        // The Gouda was logged twice, the oats once.
        assertEquals(listOf("Käse, Gouda", "Haferflocken"), state.foods.map { it.name })
        assertEquals(listOf(gouda.key, FoodKey(FoodSource.CUSTOM, oats.id.toString())), state.foods.map { it.key })
    }

    @Test
    fun theRecentChipShowsTheFoodLoggedLastWhileTheSearchPrefersTheOftenLoggedOnes() = runTest {
        val oats = testBls("B1", "Haferflocken, Schmelzflocken")
        val apple = testBls("B2", "Haferschleim Apfel")
        database.preset(oats, apple)
        // The oats were logged three times last week, the apple once today.
        diary.preset(
            testEntry(createdAtMillis = 1).copy(source = FoodSource.BLS, foodRef = "B1"),
            testEntry(createdAtMillis = 2).copy(source = FoodSource.BLS, foodRef = "B1"),
            testEntry(createdAtMillis = 3).copy(source = FoodSource.BLS, foodRef = "B1"),
            testEntry(createdAtMillis = 9).copy(source = FoodSource.BLS, foodRef = "B2"),
        )
        val viewModel = viewModel()
        assertEquals(listOf("Haferschleim Apfel", "Haferflocken, Schmelzflocken"), viewModel.current().foods.map { it.name })
        assertEquals(listOf("Haferflocken, Schmelzflocken", "Haferschleim Apfel"), viewModel.search("hafer").foods.map { it.name })
    }

    @Test
    fun aFoodLoggedOnlyOnceIsFoundEvenWhenManyOthersWereLoggedMoreOften() = runTest {
        val staples = (1..150).map { testOff("40000000${it.toString().padStart(5, '0')}", "Standard $it", popularity = 1) }
        val rare = testOff("4999999999999", "Seltener Sonderriegel", popularity = 0)
        database.preset(*staples.toTypedArray(), rare)
        staples.forEach { food ->
            repeat(2) { diary.preset(testEntry().copy(source = FoodSource.OFF, foodRef = food.key.ref)) }
        }
        diary.preset(testEntry().copy(source = FoodSource.OFF, foodRef = rare.key.ref))
        assertEquals("Seltener Sonderriegel", viewModel().search("sonderriegel").foods.first().name)
    }

    @Test
    fun aLoggedFoodThatIsGoneIsLeftOut() = runTest {
        diary.preset(testEntry().copy(source = FoodSource.OFF, foodRef = "4000000000000"))
        val state = viewModel().current()
        assertEquals(emptyList<Any>(), state.foods)
    }

    @Test
    fun withoutRecentsTheFavoritesComeNext() = runTest {
        val gouda = testBls("M110100", "Käse, Gouda")
        database.preset(gouda, testBls("B100000", "Brot, Roggen"))
        favorites.preset(gouda.key)
        custom.preset(testFood(name = "Haferflocken"))
        val state = viewModel().current()
        assertEquals(AddMode.FAVORITES, state.mode)
        assertEquals(listOf("Käse, Gouda"), state.foods.map { it.name })
    }

    @Test
    fun withoutRecentsAndFavoritesTheOwnFoodsAreShown() = runTest {
        custom.preset(testFood(name = "Haferflocken"), testFood(name = "Apfelmus", brand = null))
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
    fun theFavoritesAreSortedByName() = runTest {
        val a = testBls("A1", "Zucker")
        val b = testOff("4000000000001", "Apfelmus")
        database.preset(a, b)
        favorites.preset(a.key, b.key)
        val viewModel = viewModel()
        viewModel.selectFilter(AddFilter.FAVORITES)
        assertEquals(listOf("Apfelmus", "Zucker"), viewModel.current().foods.map { it.name })
    }

    @Test
    fun theChipsSwitchTheList() = runTest {
        val gouda = testBls("M110100", "Käse, Gouda")
        database.preset(gouda)
        favorites.preset(gouda.key)
        custom.preset(testFood(name = "Haferflocken"))
        val viewModel = viewModel()
        viewModel.state.test {
            // The state starts out null, and may already be filled by the time the first item is read.
            var first = awaitItem()
            if (first == null) first = awaitItem()
            assertEquals(AddMode.FAVORITES, first?.mode)
            viewModel.selectFilter(AddFilter.CUSTOM)
            val own = awaitItem()
            assertEquals(AddMode.CUSTOM, own?.mode)
            assertEquals(listOf("Haferflocken"), own?.foods?.map { it.name })
            viewModel.selectFilter(AddFilter.RECENT)
            assertEquals(AddMode.RECENT, awaitItem()?.mode)
        }
    }

    @Test
    fun tappingAChipDuringASearchLeavesTheSearch() = runTest {
        custom.preset(testFood(name = "Haferflocken"), testFood(name = "Apfelmus", brand = null))
        val viewModel = viewModel()
        viewModel.search("hafer")
        viewModel.selectFilter(AddFilter.CUSTOM)
        assertEquals("", viewModel.query)
        val state = viewModel.current()
        assertEquals(AddMode.CUSTOM, state.mode)
        assertEquals(2, state.foods.size)
    }

    @Test
    fun searchFindsOwnFoodsAndTheDatabaseIgnoringUmlautsAndCase() = runTest {
        custom.preset(testFood(name = "Käsebrot", brand = null))
        database.preset(testBls("M110100", "Käse, Gouda"), testOff("4000000000001", "Bio Käsescheiben", "Alnatura"))
        val viewModel = viewModel()
        assertEquals(
            listOf("Käsebrot", "Käse, Gouda", "Bio Käsescheiben"),
            viewModel.search("kase").foods.map { it.name },
        )
        assertEquals(3, viewModel.search("KAESE").foods.size)
        assertEquals(listOf("Bio Käsescheiben"), viewModel.search("alnatura").foods.map { it.name })
        assertNull(viewModel.current().selectedFilter)
    }

    @Test
    fun theSearchRanksBlsBeforeOffAndOpenFoodFactsByPopularity() = runTest {
        database.preset(
            testOff("4000000000001", "Haferflocken Billig", popularity = 1),
            testOff("4000000000002", "Haferflocken Markenware", popularity = 90),
            testBls("B1", "Haferflocken, Schmelzflocken"),
            testBls("B2", "Keks mit Haferflocken"),
        )
        val names = viewModel().search("haferflocken").foods.map { it.name }
        assertEquals(
            listOf("Haferflocken, Schmelzflocken", "Keks mit Haferflocken", "Haferflocken Markenware", "Haferflocken Billig"),
            names,
        )
    }

    @Test
    fun foodsLoggedLatelyComeFirstInASearch() = runTest {
        val oats = testBls("B1", "Haferflocken, Schmelzflocken")
        val other = testOff("4000000000001", "Haferflocken Markenware", popularity = 99)
        database.preset(oats, other)
        diary.preset(testEntry().copy(source = FoodSource.OFF, foodRef = other.key.ref, name = other.name))
        val names = viewModel().search("hafer").foods.map { it.name }
        assertEquals(listOf("Haferflocken Markenware", "Haferflocken, Schmelzflocken"), names)
    }

    @Test
    fun aFoodThatIsOwnAndRecentAppearsOnce() = runTest {
        val (oats) = custom.preset(testFood(name = "Haferflocken"))
        diary.preset(testEntry().copy(foodRef = oats.id.toString()))
        assertEquals(1, viewModel().search("hafer").foods.size)
    }

    @Test
    fun anOwnFoodIsFoundByItsBarcode() = runTest {
        custom.preset(testFood(name = "Mit Code", barcode = "4006381333931"), testFood(name = "Ohne Code"))
        assertEquals(listOf("Mit Code"), viewModel().search("4006381").foods.map { it.name })
    }

    @Test
    fun everyWordOfTheQueryHasToMatch() = runTest {
        custom.preset(testFood(name = "Haferflocken", brand = "Kölln"), testFood(name = "Haferdrink", brand = "Alpro"))
        database.preset(testBls("B1", "Haferbrei"))
        assertEquals(listOf("Haferflocken"), viewModel().search("hafer koel").foods.map { it.name })
    }

    @Test
    fun nothingFoundIsAnEmptySearchResult() = runTest {
        database.preset(testBls("B1", "Haferbrei"))
        val state = viewModel().search("xyz")
        assertEquals(AddMode.SEARCH, state.mode)
        assertEquals(emptyList<Any>(), state.foods)
        assertFalse(state.queryTooShort)
    }

    @Test
    fun aSingleLetterIsTooShortToSearch() = runTest {
        database.preset(testBls("B1", "Haferbrei"))
        val state = viewModel().search("h")
        assertTrue(state.queryTooShort)
        assertEquals(emptyList<Any>(), state.foods)
    }

    @Test
    fun clearingTheSearchGoesBackToTheChosenList() = runTest {
        custom.preset(testFood(name = "Haferflocken"))
        val viewModel = viewModel()
        viewModel.search("hafer")
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
    fun theSearchWaitsForAPauseInTyping() = runTest {
        database.preset(testBls("B1", "Haferbrei"))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        viewModel.onQueryChange("ha")
        viewModel.onQueryChange("haf")
        viewModel.onQueryChange("hafe")
        advanceTimeBy(100)
        assertEquals(0, database.searchCalls)
        assertTrue(viewModel.state.value!!.searching)
        advanceTimeBy(100)
        runCurrent()
        // Only the final text was searched, once.
        assertEquals(1, database.searchCalls)
        val state = viewModel.state.value!!
        assertFalse(state.searching)
        assertEquals(listOf("Haferbrei"), state.foods.map { it.name })
    }

    @Test
    fun whileTheDatabaseIsPreparingTheOwnFoodsStillWork() = runTest {
        database.setStatus(FoodDbStatus.Preparing)
        custom.preset(testFood(name = "Haferflocken"))
        database.preset(testBls("B1", "Haferbrei"))
        val viewModel = viewModel()
        val state = viewModel.search("hafer")
        assertEquals(FoodDbStatus.Preparing, state.dbStatus)
        assertEquals(listOf("Haferflocken"), state.foods.map { it.name })
    }

    @Test
    fun searchResultsAppearOnceTheDatabaseIsReady() = runTest {
        database.setStatus(FoodDbStatus.Preparing)
        database.preset(testBls("B1", "Haferbrei"))
        val viewModel = viewModel()
        viewModel.search("hafer")
        database.setStatus(FoodDbStatus.Ready)
        val ready = viewModel.state.first { it?.dbStatus == FoodDbStatus.Ready && it.foods.isNotEmpty() }
        assertEquals(listOf("Haferbrei"), ready?.foods?.map { it.name })
    }

    @Test
    fun aDatabaseThatFailedToOpenIsReported() = runTest {
        database.setStatus(FoodDbStatus.Failed)
        assertEquals(FoodDbStatus.Failed, viewModel().current().dbStatus)
    }

    @Test
    fun aRecentDatabaseFoodDoesNotBlockTheScreenWhileTheDatabaseIsPrepared() = runTest {
        val gouda = testBls("M110100", "Käse, Gouda")
        database.preset(gouda)
        database.setStatus(FoodDbStatus.Preparing)
        custom.preset(testFood(name = "Haferflocken"))
        diary.preset(testEntry().copy(source = FoodSource.BLS, foodRef = "M110100", name = "Käse, Gouda"))
        val viewModel = viewModel()
        // The screen has its state at once (with the hint), not only when the copy of the database is done. The recent
        // Gouda cannot be shown yet, so the own foods are listed.
        val preparing = viewModel.current()
        assertEquals(FoodDbStatus.Preparing, preparing.dbStatus)
        assertEquals(AddMode.CUSTOM, preparing.mode)
        assertEquals(listOf("Haferflocken"), preparing.foods.map { it.name })
        // Typing finds the own foods meanwhile.
        assertEquals(listOf("Haferflocken"), viewModel.search("hafer").foods.map { it.name })
        database.setStatus(FoodDbStatus.Ready)
        viewModel.selectFilter(AddFilter.RECENT)
        val ready = viewModel.state.first { it?.dbStatus == FoodDbStatus.Ready && it.foods.isNotEmpty() }
        assertEquals(listOf("Käse, Gouda"), ready?.foods?.map { it.name })
    }

    @Test
    fun aSearchFollowsChangesOfTheOwnFoods() = runTest {
        val (mine) = custom.preset(testFood(name = "Haferbrei Oma", brand = null, kcal = 100.0))
        val viewModel = viewModel()
        val before = viewModel.search("hafer")
        assertEquals(listOf("Haferbrei Oma"), before.foods.map { it.name })
        assertEquals(100.0, before.foods.single().kcalPer100, 0.0)

        custom.save(mine.copy(name = "Haferbrei Oma", nutrients = mine.nutrients.copy(kcal = 250.0)))
        assertEquals(250.0, viewModel.state.first { it?.foods?.firstOrNull()?.kcalPer100 == 250.0 }?.foods?.single()?.kcalPer100)

        custom.delete(mine.id)
        val after = viewModel.state.first { it?.mode == AddMode.SEARCH && it.foods.isEmpty() && !it.searching }
        assertEquals(emptyList<Any>(), after?.foods)
    }

    @Test
    fun aSearchFindsAFoodThatWasLoggedInTheMeantime() = runTest {
        val gouda = testBls("M110100", "Käse, Gouda")
        database.preset(gouda, testBls("M110101", "Käse, Edamer"))
        val viewModel = viewModel()
        assertEquals(listOf("Käse, Edamer", "Käse, Gouda"), viewModel.search("kase").foods.map { it.name }.sorted())
        diary.preset(testEntry().copy(source = FoodSource.BLS, foodRef = "M110100", name = "Käse, Gouda"))
        // Logged foods lead the search.
        assertEquals("Käse, Gouda", viewModel.state.first { it?.foods?.firstOrNull()?.name == "Käse, Gouda" }?.foods?.first()?.name)
    }

    @Test
    fun theEnergyUnitComesFromTheProfile() = runTest {
        profiles.preset(sampleProfile(energyUnit = EnergyUnit.KJ))
        assertEquals(EnergyUnit.KJ, viewModel().current().energyUnit)
    }
}
