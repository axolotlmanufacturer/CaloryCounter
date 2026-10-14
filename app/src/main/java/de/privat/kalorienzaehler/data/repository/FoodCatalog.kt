package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.domain.diary.FoodHit
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.LoggableFood
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Every food the user can pick, whatever its source: the user's own foods, the bundled database, the ones
 * logged lately and the favorites. The screens only talk to this.
 */
interface FoodCatalog {

    val dbStatus: StateFlow<FoodDbStatus>

    /** Starts opening the food database in the background. */
    fun prepare()

    /** All own foods, alphabetically. */
    fun observeCustom(): Flow<List<FoodHit>>

    /** The foods logged since [since], the most often logged first. */
    fun observeRecent(since: LocalDate, limit: Int): Flow<List<FoodHit>>

    /** The favorites, alphabetically. */
    fun observeFavorites(): Flow<List<FoodHit>>

    /**
     * Foods for the typed [query], ranked (PROMPT.md section 5.2): own and recently logged foods first, then BLS,
     * then Open Food Facts by popularity. Empty if the query is too short to search.
     */
    suspend fun search(query: String, today: LocalDate): List<FoodHit>

    /** The food with everything the amount screen needs, or `null` if it no longer exists. */
    suspend fun load(key: FoodKey): LoggableFood?
}
