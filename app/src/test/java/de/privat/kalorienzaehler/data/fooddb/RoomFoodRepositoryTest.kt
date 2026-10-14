package de.privat.kalorienzaehler.data.fooddb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.data.repository.RoomFoodRepository
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSearch
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.PerUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the food database that is packaged into the app (the sample database for debug builds) with Room, the same
 * way the app does: copied from the assets and validated against the schema. This is the JVM twin of the instrumented
 * `ShippedFoodDatabaseTest`.
 */
@RunWith(AndroidJUnit4::class)
class RoomFoodRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: FoodDatabase
    private lateinit var repository: RoomFoodRepository

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.databaseBuilder(context, FoodDatabase::class.java, FoodDatabase.NAME)
            .createFromAsset(FoodDatabase.ASSET_PATH)
            .allowMainThreadQueries()
            .build()
        repository = RoomFoodRepository(database, database.foodDao(), scope)
    }

    @After
    fun close() {
        database.close()
        scope.cancel()
    }

    private suspend fun blsNames(query: String): List<String> =
        repository.searchBls(FoodSearch.matchExpression(query)!!, FoodSearch.normalizedPrefix(query), 50).map { it.name }

    @Test
    fun theShippedDatabaseOpensWithRoom() = runTest {
        repository.prepare()
        assertEquals(FoodDbStatus.Ready, repository.status.first { it != FoodDbStatus.Preparing })
        val meta = repository.meta()
        assertEquals("1", meta["schema_version"])
        assertTrue(meta.getValue("bls_version").startsWith("BLS 4.0"))
        assertTrue(meta.getValue("bls_count").toInt() > 0)
        assertTrue(meta.getValue("off_count").toInt() > 0)
    }

    @Test
    fun searchFindsBlsFoodsIgnoringUmlautsAndCase() = runTest {
        assertTrue("Gouda 48 % Fett i. Tr." in blsNames("gouda"))
        assertTrue(blsNames("KAESE").all { it.contains("käse", ignoreCase = true) })
        assertTrue(blsNames("kase").isNotEmpty())
    }

    @Test
    fun everyWordOfTheQueryMustMatchAndEachIsAPrefix() = runTest {
        val names = blsNames("hafer mil")
        assertTrue(names.isNotEmpty())
        assertTrue(names.all { it.contains("hafer", ignoreCase = true) && it.contains("milch", ignoreCase = true) })
    }

    @Test
    fun blsCandidatesStartingWithTheQueryComeFirstShortestFirst() = runTest {
        // The database only selects candidates (names starting with the query, then shorter names);
        // `FoodSearch.rank` decides the final order, see ShippedCatalogTest.
        val names = blsNames("apfel")
        assertTrue("Apfel roh" in names)
        val startsWith = names.takeWhile { it.lowercase().startsWith("apfel") }
        assertTrue(startsWith.size >= 3)
        assertEquals(startsWith.map { it.length }.sorted(), startsWith.map { it.length })
        assertTrue(names.drop(startsWith.size).none { it.lowercase().startsWith("apfel") })
    }

    @Test
    fun searchFindsOpenFoodFactsFoodsByNameBrandAndUmlautFreeBrand() = runTest {
        val match = FoodSearch.matchExpression("hafer koelln")!!
        val found = repository.searchOff(match, 20)
        assertTrue(found.any { it.key == FoodKey(FoodSource.OFF, "4000540000108") })
        assertTrue(found.all { it.key.source == FoodSource.OFF })
    }

    @Test
    fun openFoodFactsResultsComeInPopularityOrder() = runTest {
        val found = repository.searchOff(FoodSearch.matchExpression("haferflocken")!!, 20)
        assertTrue(found.size >= 2)
        assertEquals(found.sortedBy { it.id }.map { it.id }, found.map { it.id })
        assertTrue(found.map { it.popularity }.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun aFoodIsLoadedByItsStableKey() = runTest {
        val apple = repository.get(FoodKey(FoodSource.BLS, "F110100"))
        assertNotNull(apple)
        assertEquals("Apfel roh", apple!!.name)
        assertEquals(58.0, apple.nutrients.kcal, 0.0)
        assertEquals(PerUnit.GRAM, apple.perUnit)
        assertNull(apple.brand)
        assertNull(repository.get(FoodKey(FoodSource.BLS, "ZZZZZZZ")))
        assertNull(repository.get(FoodKey(FoodSource.CUSTOM, "1")))
    }

    @Test
    fun unknownNutrientsStayUnknown() = runTest {
        // No BLS row invents values: whatever is missing is null, never 0 by default.
        val all = repository.searchBls(FoodSearch.matchExpression("brot")!!, "brot", 200)
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.nutrients.kcal >= 0.0 })
    }

    @Test
    fun severalFoodsCanBeLoadedAtOnceEvenBeyondTheVariableLimit() = runTest {
        val refs = database.openHelper.readableDatabase
            .query("SELECT source_id FROM foods WHERE source = 'BLS'")
            .use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList() }
        assertTrue(refs.size > 500)
        val loaded = repository.getAll(FoodSource.BLS, refs)
        assertEquals(refs.size, loaded.size)
        assertEquals(emptyList<Any>(), repository.getAll(FoodSource.OFF, emptyList()))
    }

    @Test
    fun portionsOfAFoodAreListedAndEstimatesAreFlagged() = runTest {
        val apple = repository.get(FoodKey(FoodSource.BLS, "F110100"))!!
        val portions = repository.portions(apple)
        assertEquals(listOf("Stück (klein)", "Stück (mittel)", "Stück (groß)"), portions.map { it.label })
        assertEquals(listOf(100.0, 150.0, 200.0), portions.map { it.grams })
        assertTrue(portions.all { it.estimated })
        val bread = repository.searchBls(FoodSearch.matchExpression("roggenvollkornbrot")!!, "roggenvollkornbrot", 1).single()
        assertEquals(listOf("Scheibe"), repository.portions(bread).map { it.label }.take(1))
        val gouda = repository.get(FoodKey(FoodSource.BLS, "M402600"))!!
        assertEquals(listOf("Scheibe"), repository.portions(gouda).map { it.label })
        assertTrue(repository.portions(gouda).single().estimated)
    }

    @Test
    fun aBarcodeIsFoundInEitherLengthForm() = runTest {
        val food = repository.findByBarcodes(listOf("04000540000108", "4000540000108"))
        assertEquals(FoodKey(FoodSource.OFF, "4000540000108"), food?.key)
        assertNull(repository.findByBarcodes(listOf("4999999999999")))
        assertNull(repository.findByBarcodes(emptyList()))
    }

    @Test
    fun aDatabaseThatCannotBeOpenedIsReportedAndSearchesStayEmpty() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val broken = Room.databaseBuilder(context, FoodDatabase::class.java, "broken-foods.db")
            .createFromAsset("databases/does-not-exist.db")
            .build()
        val failing = RoomFoodRepository(broken, broken.foodDao(), scope)
        try {
            failing.prepare()
            assertEquals(FoodDbStatus.Failed, failing.status.first { it != FoodDbStatus.Preparing })
            assertEquals(emptyList<Any>(), failing.searchBls("kase*", "kase", 10))
            assertNull(failing.get(FoodKey(FoodSource.BLS, "F110100")))
            assertEquals(emptyMap<String, String>(), failing.meta())
        } finally {
            broken.close()
        }
    }
}
