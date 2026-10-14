package de.privat.kalorienzaehler.data.repository

import androidx.room.withTransaction
import de.privat.kalorienzaehler.data.userdb.DiaryDao
import de.privat.kalorienzaehler.data.userdb.UserDatabase
import de.privat.kalorienzaehler.data.userdb.toDomain
import de.privat.kalorienzaehler.data.userdb.toEntity
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.Meal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDiaryRepository @Inject constructor(
    private val database: UserDatabase,
    private val dao: DiaryDao,
) : DiaryRepository {

    override fun observeDay(date: LocalDate): Flow<List<DiaryEntry>> =
        dao.observeDay(date).map { entries -> entries.map { it.toDomain() } }

    override suspend fun get(id: Long): DiaryEntry? = dao.get(id)?.toDomain()

    override fun observeRecentFoods(since: LocalDate, limit: Int): Flow<List<FoodKey>> =
        dao.observeRecentFoods(since, limit).map { rows -> rows.map { FoodKey(it.source, it.ref) } }

    override suspend fun add(entry: DiaryEntry): Long = dao.insert(entry.toEntity())

    override suspend fun update(entry: DiaryEntry) = dao.update(entry.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun restore(entry: DiaryEntry) {
        dao.insert(entry.toEntity())
    }

    override suspend fun copyMeal(from: LocalDate, to: LocalDate, meal: Meal, createdAtMillis: Long): List<Long> =
        database.withTransaction {
            // Only an empty meal is filled: a second tap (or a second call) cannot duplicate the copy.
            if (dao.getMeal(to, meal).isNotEmpty()) return@withTransaction emptyList()
            val copies = dao.getMeal(from, meal).map { it.toDomain().copiedTo(to, createdAtMillis).toEntity() }
            dao.insertAll(copies)
        }

    override suspend fun deleteAll(ids: List<Long>) = dao.deleteAll(ids)

    override suspend fun deleteEverything() = dao.deleteEverything()
}
