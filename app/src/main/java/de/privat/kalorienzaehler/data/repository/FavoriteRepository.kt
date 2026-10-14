package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.domain.diary.FoodKey
import kotlinx.coroutines.flow.Flow

/** The foods the user marked with a star. */
interface FavoriteRepository {

    fun observeAll(): Flow<List<FoodKey>>

    fun observeIsFavorite(key: FoodKey): Flow<Boolean>

    suspend fun setFavorite(key: FoodKey, favorite: Boolean)

    /** Removes all favorites ("Alle Daten löschen"). */
    suspend fun deleteEverything()
}
