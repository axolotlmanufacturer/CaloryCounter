package de.privat.kalorienzaehler.data.fooddb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.data.repository.RoomFoodRepository
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeFavoriteRepository
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.testEntry
import de.privat.kalorienzaehler.testing.testFood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real search, ranking and loading on top of the food database that is packaged into the app. */
@RunWith(AndroidJUnit4::class)
class ShippedCatalogTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: FoodDatabase
    private lateinit var catalog: DefaultFoodCatalog
    private val custom = FakeCustomFoodRepository()
    private val diary = FakeDiaryRepository()
    private val favorites = FakeFavoriteRepository()

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.databaseBuilder(context, FoodDatabase::class.java, FoodDatabase.NAME)
            .createFromAsset(FoodDatabase.ASSET_PATH)
            .allowMainThreadQueries()
            .build()
        val foods = RoomFoodRepository(database.foodDao(), scope, NoFoodDbContentGuard)
        catalog = DefaultFoodCatalog(foods, custom, favorites, diary)
        catalog.prepare()
        // Searches skip the database while it is still being unpacked (the screen searches again when it is ready).
        assertEquals(FoodDbStatus.Ready, runBlocking { catalog.dbStatus.first { it != FoodDbStatus.Preparing } })
    }

    @After
    fun close() {
        database.close()
        scope.cancel()
    }

    private suspend fun names(query: String) = catalog.search(query, TEST_TODAY).map { it.name }

    @Test
    fun theWholeFoodNameBeatsLongerNamesThatMerelyStartTheSame() = runTest {
        // "Apfelmus" is shorter than "Apfel roh", but the user typed the word "Apfel".
        assertEquals("Apfel roh", names("apfel").first())
    }

    @Test
    fun blsComesBeforeOpenFoodFactsAndEveryFoodAppearsOnce() = runTest {
        val hits = catalog.search("haferflocken", TEST_TODAY)
        val sources = hits.map { it.key.source }
        assertTrue(FoodSource.BLS in sources && FoodSource.OFF in sources)
        assertEquals(sources.sortedBy { it.ordinal }, sources)
        assertEquals(hits.size, hits.map { it.key }.toSet().size)
    }

    @Test
    fun searchResultsAreCappedAt50() = runTest {
        // "ge" matches hundreds of foods in the sample database.
        assertEquals(50, catalog.search("ge", TEST_TODAY).size)
    }

    @Test
    fun brandsAndUmlautsAreSearchable() = runTest {
        val hits = catalog.search("hafer koelln", TEST_TODAY)
        assertTrue(hits.any { it.key == FoodKey(FoodSource.OFF, "4000540000108") })
        assertTrue(hits.first { it.key.source == FoodSource.OFF }.brand.orEmpty().isNotEmpty())
    }

    @Test
    fun ownFoodsAndRecentlyLoggedFoodsComeFirst() = runTest {
        val (mine) = custom.preset(testFood(name = "Apfelkuchen Oma", brand = null))
        assertEquals("Apfelkuchen Oma", names("apfel").first())
        diary.preset(testEntry().copy(source = FoodSource.BLS, foodRef = "F110600", name = "Apfelsaft"))
        // Recently logged first, then the own foods, then the ranked database.
        assertEquals(listOf("Apfelsaft", mine.name), names("apfel").take(2))
        assertEquals("Apfel roh", names("apfel")[2])
    }

    @Test
    fun recentAndFavoriteFoodsResolveAcrossSources() = runTest {
        val (mine) = custom.preset(testFood(name = "Haferbrei"))
        favorites.preset(
            FoodKey(FoodSource.BLS, "F110100"),
            FoodKey(FoodSource.OFF, "4000540000108"),
            FoodKey(FoodSource.CUSTOM, mine.id.toString()),
            FoodKey(FoodSource.OFF, "4999999999999"), // no longer in the database
        )
        val hits = catalog.observeFavorites().first()
        assertEquals(listOf("Apfel roh", "Haferbrei", "Haferflocken, Blütenzarte Köllnflocken"), hits.map { it.name })
        diary.preset(testEntry().copy(source = FoodSource.OFF, foodRef = "4000540000108"))
        assertEquals(
            listOf(FoodKey(FoodSource.OFF, "4000540000108")),
            catalog.observeRecent(TEST_TODAY.minusDays(30), 30).first().map { it.key },
        )
    }

    @Test
    fun aLoadedFoodCarriesItsPortionsAndUnits() = runTest {
        val apple = catalog.load(FoodKey(FoodSource.BLS, "F110100"))
        assertNotNull(apple)
        assertEquals(PerUnit.GRAM, apple!!.perUnit)
        assertEquals(listOf("g", "Stück (klein)", "Stück (mittel)", "Stück (groß)"), apple.units().map { it.key })
        assertEquals(listOf(false, true, true, true), apple.units().map { it.estimated })
        assertNull(catalog.load(FoodKey(FoodSource.BLS, "NOPE")))
        assertNull(catalog.load(FoodKey(FoodSource.CUSTOM, "12")))
    }

    @Test
    fun aTooShortQueryFindsNothing() = runTest {
        assertEquals(emptyList<String>(), names("a"))
        assertEquals(emptyList<String>(), names("   "))
    }
}
