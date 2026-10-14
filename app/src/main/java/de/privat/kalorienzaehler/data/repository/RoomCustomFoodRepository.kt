package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.userdb.CustomFoodDao
import de.privat.kalorienzaehler.data.userdb.toDomain
import de.privat.kalorienzaehler.data.userdb.toEntity
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.text.TextNormalizer
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCustomFoodRepository @Inject constructor(
    private val dao: CustomFoodDao,
) : CustomFoodRepository {

    override fun observeAll(): Flow<List<CustomFood>> =
        dao.observeAll().map { foods ->
            // "Äpfel" sorts with the A's, "Öl" with the O's (the search normalization decides the order).
            foods.map { it.toDomain() }.sortedWith(compareBy({ TextNormalizer.normalize(it.name) }, { it.id }))
        }

    override fun observeRecent(since: LocalDate, limit: Int): Flow<List<CustomFood>> =
        dao.observeRecent(since, limit).map { foods -> foods.map { it.toDomain() } }

    override suspend fun get(id: Long): CustomFood? = dao.get(id)?.toDomain()

    override suspend fun save(food: CustomFood): Long =
        if (food.id == 0L) {
            dao.insert(food.toEntity())
        } else {
            dao.update(food.toEntity())
            food.id
        }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteEverything() = dao.deleteEverything()
}
