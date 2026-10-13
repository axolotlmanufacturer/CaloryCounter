package de.privat.kalorienzaehler.data.userdb

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.privat.kalorienzaehler.data.userdb.entity.WeightEntryEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_entry ORDER BY date ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entry WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<WeightEntryEntity>>

    @Upsert
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entry WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM weight_entry")
    suspend fun deleteAll()
}
