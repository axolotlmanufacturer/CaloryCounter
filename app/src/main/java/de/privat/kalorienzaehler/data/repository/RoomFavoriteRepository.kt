package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.userdb.FavoriteDao
import de.privat.kalorienzaehler.data.userdb.entity.FavoriteEntity
import de.privat.kalorienzaehler.domain.diary.FoodKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoriteRepository @Inject constructor(
    private val dao: FavoriteDao,
) : FavoriteRepository {

    override fun observeAll(): Flow<List<FoodKey>> =
        dao.observeAll().map { rows -> rows.map { FoodKey(it.foodSource, it.foodRef) } }

    override fun observeIsFavorite(key: FoodKey): Flow<Boolean> = dao.observeIsFavorite(key.source, key.ref)

    override suspend fun setFavorite(key: FoodKey, favorite: Boolean) {
        if (favorite) dao.insert(FavoriteEntity(key.source, key.ref)) else dao.delete(key.source, key.ref)
    }

    override suspend fun deleteEverything() = dao.deleteEverything()
}
