package de.privat.kalorienzaehler.data.userdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.privat.kalorienzaehler.data.userdb.entity.CustomFoodEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {

    @Query("SELECT * FROM custom_food ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<CustomFoodEntity>>

    /**
     * Custom foods logged since [since], the most often used first (ties: the most recent first).
     * A diary entry points at its food by `food_ref` = the food's id as text.
     */
    @Query(
        """
        SELECT cf.* FROM custom_food cf
        JOIN (
            SELECT food_ref AS ref, COUNT(*) AS uses, MAX(created_at) AS last_used
            FROM diary_entry
            WHERE food_source = 'CUSTOM' AND date >= :since
            GROUP BY food_ref
        ) d ON d.ref = CAST(cf.id AS TEXT)
        ORDER BY d.uses DESC, d.last_used DESC, cf.id
        LIMIT :limit
        """,
    )
    fun observeRecent(since: LocalDate, limit: Int): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_food WHERE id = :id")
    suspend fun get(id: Long): CustomFoodEntity?

    @Query("SELECT * FROM custom_food WHERE barcode = :barcode ORDER BY id LIMIT 1")
    suspend fun findByBarcode(barcode: String): CustomFoodEntity?

    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Update
    suspend fun update(food: CustomFoodEntity)

    @Query("DELETE FROM custom_food WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_food")
    suspend fun deleteEverything()
}
