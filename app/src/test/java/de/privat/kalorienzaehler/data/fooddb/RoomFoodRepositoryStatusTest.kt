package de.privat.kalorienzaehler.data.fooddb

import de.privat.kalorienzaehler.data.repository.RoomFoodRepository
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Preparing, Ready and Failed of the food database, with a DAO that fails on demand. */
class RoomFoodRepositoryStatusTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun stop() = scope.cancel()

    /** Opens fine, except for the first [failures] attempts. Everything else is empty. */
    private class ScriptedDao(var failures: Int, private val events: MutableList<String>) : FoodDao {
        val opens = AtomicInteger()

        override suspend fun count(): Int {
            opens.incrementAndGet()
            events += "count"
            if (failures > 0) {
                failures--
                throw IOException("Unable to copy database file.")
            }
            return 1
        }

        override suspend fun searchBls(match: String, prefix: String, limit: Int) = emptyList<FoodEntity>()

        override suspend fun searchOff(match: String, limit: Int) = emptyList<FoodEntity>()

        override suspend fun find(source: FoodSource, sourceId: String): FoodEntity? = null

        override suspend fun findAll(source: FoodSource, sourceIds: List<String>) = emptyList<FoodEntity>()

        override suspend fun findByBarcodes(barcodes: List<String>): FoodEntity? = null

        override suspend fun portions(foodId: Long) = emptyList<FoodPortionEntity>()

        override suspend fun meta() = emptyList<FoodMetaEntity>()
    }

    private class RecordingGuard(private val events: MutableList<String>) : FoodDbContentGuard {
        override fun discardStaleCopy() {
            events += "discard"
        }

        override fun markCurrent() {
            events += "mark"
        }
    }

    private suspend fun RoomFoodRepository.settled() = withTimeout(10_000) { status.first { it != FoodDbStatus.Preparing } }

    @Test
    fun theStaleCopyIsDiscardedBeforeTheFirstQueryAndTheMarkIsSetAfterwards() = runBlocking {
        val events = CopyOnWriteArrayList<String>()
        val repository = RoomFoodRepository(ScriptedDao(0, events), scope, RecordingGuard(events))
        repository.prepare()
        assertEquals(FoodDbStatus.Ready, repository.settled())
        assertEquals(listOf("discard", "count", "mark"), events)
    }

    @Test
    fun aFailedOpenIsNotMarkedAsCurrent() = runBlocking {
        val events = CopyOnWriteArrayList<String>()
        val repository = RoomFoodRepository(ScriptedDao(1, events), scope, RecordingGuard(events))
        repository.prepare()
        assertEquals(FoodDbStatus.Failed, repository.settled())
        assertEquals(listOf("discard", "count"), events)
    }

    @Test
    fun anotherPrepareAfterAFailureTriesAgain() = runBlocking {
        val events = CopyOnWriteArrayList<String>()
        val dao = ScriptedDao(1, events)
        val repository = RoomFoodRepository(dao, scope, RecordingGuard(events))
        repository.prepare()
        assertEquals(FoodDbStatus.Failed, repository.settled())
        // The user freed some storage and opens the add screen again.
        repository.prepare()
        assertEquals(FoodDbStatus.Ready, repository.settled())
        assertEquals(2, dao.opens.get())
        assertEquals(listOf("discard", "count", "discard", "count", "mark"), events)
    }

    @Test
    fun aWorkingDatabaseIsOpenedOnlyOnce() = runBlocking {
        val dao = ScriptedDao(0, CopyOnWriteArrayList())
        val repository = RoomFoodRepository(dao, scope, NoFoodDbContentGuard)
        repeat(5) { repository.prepare() }
        assertEquals(FoodDbStatus.Ready, repository.settled())
        repeat(3) { repository.prepare() }
        repository.searchBls("a*", "a", 10)
        assertEquals(1, dao.opens.get())
    }

    @Test
    fun lookupsDoNotRetryAndFindNothingAfterAFailure() = runBlocking {
        val dao = ScriptedDao(100, CopyOnWriteArrayList())
        val repository = RoomFoodRepository(dao, scope, NoFoodDbContentGuard)
        repository.prepare()
        assertEquals(FoodDbStatus.Failed, repository.settled())
        val attempts = dao.opens.get()
        assertEquals(emptyList<Any>(), repository.searchBls("kase*", "kase", 10))
        assertNull(repository.get(FoodKey(FoodSource.BLS, "F110100")))
        assertEquals(emptyMap<String, String>(), repository.meta())
        assertEquals(attempts, dao.opens.get())
    }

    @Test
    fun aLookupBeforeAnyPrepareStartsTheOpeningItself() = runBlocking {
        val dao = ScriptedDao(0, CopyOnWriteArrayList())
        val repository = RoomFoodRepository(dao, scope, NoFoodDbContentGuard)
        assertNull(withTimeout(10_000) { repository.get(FoodKey(FoodSource.BLS, "F110100")) })
        assertEquals(FoodDbStatus.Ready, repository.status.value)
        assertEquals(1, dao.opens.get())
    }
}
