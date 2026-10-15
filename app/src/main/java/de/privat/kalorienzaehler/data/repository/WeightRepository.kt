package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.domain.weight.WeightPoint
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Body weight entries, at most one per day. The current weight is the latest entry. */
interface WeightRepository {

    /** All entries, oldest first. */
    fun observeEntries(): Flow<List<WeightPoint>>

    /** The most recent entry, or `null` if there is none yet. */
    fun observeLatest(): Flow<WeightPoint?>

    /** Saves [kg] for [date], replacing an existing entry of that day. */
    suspend fun upsert(date: LocalDate, kg: Double)

    suspend fun delete(date: LocalDate)
}
