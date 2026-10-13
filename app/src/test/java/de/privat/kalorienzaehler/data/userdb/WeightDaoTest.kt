package de.privat.kalorienzaehler.data.userdb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import de.privat.kalorienzaehler.data.repository.RoomWeightRepository
import de.privat.kalorienzaehler.data.userdb.entity.WeightEntryEntity
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightDaoTest {

    private lateinit var database: UserDatabase
    private lateinit var dao: WeightDao

    private val oct1 = LocalDate.of(2026, 10, 1)
    private fun day(n: Long): LocalDate = oct1.plusDays(n)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.weightDao()
    }

    @After
    fun close() = database.close()

    @Test
    fun entriesComeBackOldestFirst() = runTest {
        dao.upsert(WeightEntryEntity(day(5), 79.0))
        dao.upsert(WeightEntryEntity(day(0), 80.0))
        dao.upsert(WeightEntryEntity(day(9), 78.5))
        dao.observeAll().test {
            assertEquals(listOf(day(0), day(5), day(9)), awaitItem().map { it.date })
        }
    }

    @Test
    fun savingTheSameDayReplacesTheValue() = runTest {
        dao.upsert(WeightEntryEntity(day(0), 80.0, note = "morgens"))
        dao.upsert(WeightEntryEntity(day(0), 79.6))
        dao.observeAll().test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals(79.6, entries.single().kg, 0.0)
        }
    }

    @Test
    fun latestIsTheNewestDateNotTheLastInserted() = runTest {
        dao.upsert(WeightEntryEntity(day(9), 78.5))
        dao.upsert(WeightEntryEntity(day(0), 80.0))
        dao.observeLatest().test {
            assertEquals(day(9), awaitItem()?.date)
        }
    }

    @Test
    fun latestIsNullWithoutEntries() = runTest {
        dao.observeLatest().test { assertNull(awaitItem()) }
    }

    @Test
    fun rangeQueryIsInclusiveAndSortedByDate() = runTest {
        (0L..9L).forEach { dao.upsert(WeightEntryEntity(day(it), 80.0 - it)) }
        dao.observeBetween(day(3), day(5)).test {
            assertEquals(listOf(day(3), day(4), day(5)), awaitItem().map { it.date })
        }
    }

    @Test
    fun deleteRemovesOneDayAndDeleteAllEverything() = runTest {
        (0L..2L).forEach { dao.upsert(WeightEntryEntity(day(it), 80.0)) }
        dao.delete(day(1))
        dao.observeAll().test {
            assertEquals(listOf(day(0), day(2)), awaitItem().map { it.date })
            dao.deleteAll()
            assertEquals(emptyList<WeightEntryEntity>(), awaitItem())
        }
    }

    @Test
    fun observersAreToldAboutChanges() = runTest {
        dao.observeLatest().test {
            assertNull(awaitItem())
            dao.upsert(WeightEntryEntity(day(0), 80.0))
            assertEquals(80.0, awaitItem()?.kg)
            dao.upsert(WeightEntryEntity(day(1), 79.5))
            assertEquals(79.5, awaitItem()?.kg)
        }
    }

    @Test
    fun repositoryMapsToDomainPoints() = runTest {
        val repository = RoomWeightRepository(dao)
        repository.upsert(day(0), 80.0)
        repository.upsert(day(2), 79.0)
        repository.observeEntries().test {
            assertEquals(listOf(WeightPoint(day(0), 80.0), WeightPoint(day(2), 79.0)), awaitItem())
        }
        repository.observeLatest().test {
            assertEquals(WeightPoint(day(2), 79.0), awaitItem())
        }
        repository.delete(day(2))
        repository.observeLatest().test {
            assertEquals(WeightPoint(day(0), 80.0), awaitItem())
        }
    }
}
