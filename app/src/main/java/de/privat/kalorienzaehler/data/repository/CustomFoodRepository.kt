package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.domain.diary.CustomFood
import kotlinx.coroutines.flow.Flow

/** Foods the user entered by hand (values as printed on the pack). */
interface CustomFoodRepository {

    /** All custom foods, alphabetically. */
    fun observeAll(): Flow<List<CustomFood>>

    suspend fun get(id: Long): CustomFood?

    /** The first own food whose barcode is one of these spellings of a code, or `null`. */
    suspend fun findByBarcodes(barcodes: List<String>): CustomFood?

    /** Inserts the food (id 0) or updates it, and returns its id. */
    suspend fun save(food: CustomFood): Long

    /** Deletes the food. Diary entries that used it keep their snapshot. */
    suspend fun delete(id: Long)

    /** Removes all custom foods ("Alle Daten löschen"). */
    suspend fun deleteEverything()
}
