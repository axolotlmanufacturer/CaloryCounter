package de.privat.kalorienzaehler.data.fooddb

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.data.repository.RoomFoodRepository
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSearch
import de.privat.kalorienzaehler.domain.diary.FoodSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the `foods.db` that is packaged into the APK being tested with Room, exactly like the app does (PROMPT.md §7.4):
 * copied from the assets and validated against the exported schema. Run it with `./gradlew connectedDebugAndroidTest`
 * (sample database) or `-PfoodDb=full connectedDebugAndroidTest` (full database; also checks the search speed).
 *
 * The same checks against the sample database run on the JVM in `RoomFoodRepositoryTest`.
 */
@RunWith(AndroidJUnit4::class)
class ShippedFoodDatabaseTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var context: Context
    private lateinit var database: FoodDatabase
    private lateinit var repository: RoomFoodRepository

    @Before
    fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB_NAME)
        database = Room.databaseBuilder(context, FoodDatabase::class.java, TEST_DB_NAME)
            .createFromAsset(FoodDatabase.ASSET_PATH)
            .build()
        repository = RoomFoodRepository(database.foodDao(), scope, NoFoodDbContentGuard)
    }

    @After
    fun close() {
        database.close()
        context.deleteDatabase(TEST_DB_NAME)
        scope.cancel()
    }

    private fun ready() = runBlocking { repository.status.first { it != FoodDbStatus.Preparing } }

    private fun search(query: String): List<String> = runBlocking {
        val match = checkNotNull(FoodSearch.matchExpression(query))
        val bls = repository.searchBls(match, FoodSearch.normalizedPrefix(query), 120)
        val off = repository.searchOff(match, 120)
        (bls + off).map { it.name }
    }

    @Test
    fun theShippedDatabaseOpensWithRoomAndPassesItsSchemaCheck() {
        repository.prepare()
        assertEquals(FoodDbStatus.Ready, ready())
        val meta = runBlocking { repository.meta() }
        assertEquals("1", meta["schema_version"])
        assertTrue(meta.getValue("bls_version").startsWith("BLS 4.0"))
        assertTrue(meta.getValue("bls_count").toInt() > 0)
        assertTrue(meta.getValue("off_count").toInt() > 0)
    }

    @Test
    fun searchAndLookupWork() {
        repository.prepare()
        assertEquals(FoodDbStatus.Ready, ready())
        assertTrue(search("apfel").isNotEmpty())
        val apple = runBlocking { repository.get(FoodKey(FoodSource.BLS, "F110100")) }
        assertNotNull(apple)
        assertEquals("Apfel roh", apple!!.name)
    }

    /** The full database must answer typical queries in under 150 ms (PROMPT.md §5.2). */
    @Test
    fun searchIsFastOnTheFullDatabase() {
        repository.prepare()
        assertEquals(FoodDbStatus.Ready, ready())
        val meta = runBlocking { repository.meta() }
        assumeTrue("only meaningful with -PfoodDb=full", meta["sample"] != "true")

        val queries = listOf("kase", "kase gouda", "haferflocken", "milch", "schokolade riegel", "brot", "joghurt natur", "ka", "coca cola")
        // Warm up the page cache, then take the slowest of three runs per query.
        queries.forEach { search(it) }
        val slowest = queries.associateWith { query ->
            (1..3).maxOf { measureMillis { search(query) } }
        }
        Log.i(TAG, "search timings (ms): $slowest")
        slowest.forEach { (query, millis) -> assertTrue("'$query' took $millis ms", millis < 150) }
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        const val TAG = "FoodDbPerf"
        const val TEST_DB_NAME = "foods-under-test.db"
    }
}
