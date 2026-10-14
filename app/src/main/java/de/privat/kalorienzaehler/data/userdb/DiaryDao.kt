package de.privat.kalorienzaehler.data.userdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.privat.kalorienzaehler.data.userdb.entity.DiaryEntryEntity
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    /** The entries of one day, oldest first. Grouping by meal happens in the domain. */
    @Query("SELECT * FROM diary_entry WHERE date = :date ORDER BY created_at, id")
    fun observeDay(date: LocalDate): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entry WHERE date = :date AND meal = :meal ORDER BY created_at, id")
    suspend fun getMeal(date: LocalDate, meal: Meal): List<DiaryEntryEntity>

    /**
     * The foods logged since [since], the most often logged first (ties: the latest first). Quick entries are no
     * foods. A food is identified by its source and reference, whatever name it was logged under.
     */
    @Query(
        """
        SELECT food_source AS source, food_ref AS ref FROM diary_entry
        WHERE food_source IN ('CUSTOM', 'BLS', 'OFF') AND food_ref IS NOT NULL AND date >= :since
        GROUP BY food_source, food_ref
        ORDER BY COUNT(*) DESC, MAX(created_at) DESC, MAX(id) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentFoods(since: LocalDate, limit: Int): Flow<List<RecentFoodRow>>

    @Query("SELECT * FROM diary_entry WHERE id = :id")
    suspend fun get(id: Long): DiaryEntryEntity?

    /** Stores the entry; one with an existing id (undo of a delete) takes its old place again. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DiaryEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM diary_entry WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM diary_entry")
    suspend fun deleteEverything()
}

/** A food from the diary: where it came from and its reference there. */
data class RecentFoodRow(val source: FoodSource, val ref: String)
