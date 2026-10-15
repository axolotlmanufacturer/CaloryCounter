package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.domain.barcode.Barcode
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

    /** The foods logged since [since], the one logged last first ("Zuletzt"). */
    fun observeRecent(since: LocalDate, limit: Int): Flow<List<FoodHit>>

    /** The favorites, alphabetically. */
    fun observeFavorites(): Flow<List<FoodHit>>

    /**
     * Foods for the typed [query], ranked (PROMPT.md section 5.2): own and recently logged foods first, then BLS,
     * then Open Food Facts by popularity. Empty if the query is too short to search. The result is a flow: it is
     * computed again when an own food or the diary changes, or when the food database becomes ready, and while the
     * database is still being unpacked it holds the own foods alone.
     */
    fun observeSearch(query: String, today: LocalDate): Flow<List<FoodHit>>

    /** The first result of [observeSearch]. */
    suspend fun search(query: String, today: LocalDate): List<FoodHit>

    /**
     * Looks a scanned or typed barcode up: the user's own foods first, then the food database (PROMPT.md 7.5). Waits
     * for the database if it is still being prepared.
     */
    suspend fun findByBarcode(barcode: Barcode): BarcodeLookup

    /** The food with everything the amount screen needs, or `null` if it no longer exists. */
    suspend fun load(key: FoodKey): LoggableFood?
}

/** What a barcode found. */
sealed interface BarcodeLookup {
    data class Found(val food: FoodHit) : BarcodeLookup

    /** Nothing has this code. [databaseUnavailable]: the food database could not be opened, so only own foods were searched. */
    data class NotFound(val databaseUnavailable: Boolean) : BarcodeLookup
}
