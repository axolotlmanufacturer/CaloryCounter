package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.domain.diary.CustomFood
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Foods the user entered by hand (values as printed on the pack). */
interface CustomFoodRepository {

    /** All custom foods, alphabetically. */
    fun observeAll(): Flow<List<CustomFood>>

    /** Custom foods logged since [since], the most often used first. */
    fun observeRecent(since: LocalDate, limit: Int): Flow<List<CustomFood>>

    suspend fun get(id: Long): CustomFood?

    /** Inserts the food (id 0) or updates it, and returns its id. */
    suspend fun save(food: CustomFood): Long

    /** Deletes the food. Diary entries that used it keep their snapshot. */
    suspend fun delete(id: Long)

    /** Removes all custom foods ("Alle Daten löschen"). */
    suspend fun deleteEverything()
}
