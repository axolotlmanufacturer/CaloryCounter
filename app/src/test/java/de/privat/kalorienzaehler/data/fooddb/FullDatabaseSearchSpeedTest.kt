package de.privat.kalorienzaehler.data.fooddb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.data.repository.RoomFoodRepository
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeFavoriteRepository
import de.privat.kalorienzaehler.testing.TEST_TODAY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search speed of the whole path (FTS candidates, ranking) on the FULL food database, run on the JVM as a sanity check
 * of the queries and indexes (PROMPT.md §5.2: under 150 ms). Skipped for the sample database; run with
 * `./gradlew :app:testDebugUnitTest --tests '*FullDatabaseSearchSpeedTest' -PfoodDb=full -i`. The phone is slower
 * than a laptop, `ShippedFoodDatabaseTest` repeats the measurement there.
 */
@RunWith(AndroidJUnit4::class)
class FullDatabaseSearchSpeedTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: FoodDatabase
    private lateinit var catalog: DefaultFoodCatalog
    private lateinit var repository: RoomFoodRepository

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.databaseBuilder(context, FoodDatabase::class.java, FoodDatabase.NAME)
            .createFromAsset(FoodDatabase.ASSET_PATH)
            .allowMainThreadQueries()
            .build()
        repository = RoomFoodRepository(database.foodDao(), scope, NoFoodDbContentGuard)
        catalog = DefaultFoodCatalog(repository, FakeCustomFoodRepository(), FakeFavoriteRepository(), FakeDiaryRepository())
        catalog.prepare()
        runBlocking { catalog.dbStatus.first { it != FoodDbStatus.Preparing } }
    }

    @After
    fun close() {
        database.close()
        scope.cancel()
    }

    @Test
    fun typicalQueriesAnswerInUnder150Milliseconds() = runBlocking {
        assumeTrue("only meaningful with -PfoodDb=full", repository.meta()["sample"] != "true")
        val queries = listOf("ka", "kase", "kase gouda", "haferflocken", "milch", "schokolade riegel", "brot", "joghurt natur", "a b", "coca cola")
        queries.forEach { catalog.search(it, TEST_TODAY) } // warm up
        val slowest = queries.associateWith { query ->
            (1..3).maxOf {
                val start = System.nanoTime()
                catalog.search(query, TEST_TODAY)
                (System.nanoTime() - start) / 1_000_000
            }
        }
        println("search timings (ms, slowest of 3): $slowest")
        slowest.forEach { (query, millis) -> assertTrue("'$query' took $millis ms", millis < 150) }
    }
}
