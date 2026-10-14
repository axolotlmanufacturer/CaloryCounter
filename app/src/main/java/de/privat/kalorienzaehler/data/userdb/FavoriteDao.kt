package de.privat.kalorienzaehler.data.userdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.privat.kalorienzaehler.data.userdb.entity.FavoriteEntity
import de.privat.kalorienzaehler.domain.diary.FoodSource
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE food_source = :source AND food_ref = :ref)")
    fun observeIsFavorite(source: FoodSource, ref: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE food_source = :source AND food_ref = :ref")
    suspend fun delete(source: FoodSource, ref: String)

    @Query("DELETE FROM favorite")
    suspend fun deleteEverything()
}
