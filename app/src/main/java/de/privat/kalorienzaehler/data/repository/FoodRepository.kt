package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.domain.diary.Food
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodPortion
import de.privat.kalorienzaehler.domain.diary.FoodSource
import kotlinx.coroutines.flow.StateFlow

/** The bundled, read-only food database (BLS + Open Food Facts). */
interface FoodRepository {

    val status: StateFlow<FoodDbStatus>

    /** Starts opening the database in the background (copying it on first use). Safe to call again. */
    fun prepare()

    /** BLS foods for a full-text [match] expression, best first; empty while the database is unavailable. */
    suspend fun searchBls(match: String, prefix: String, limit: Int): List<Food>

    /** Open Food Facts foods for a full-text [match] expression, most scanned first. */
    suspend fun searchOff(match: String, limit: Int): List<Food>

    suspend fun get(key: FoodKey): Food?

    /** Several foods of one source (favorites, recently logged foods), in no particular order. */
    suspend fun getAll(source: FoodSource, refs: List<String>): List<Food>

    /** The food with one of these barcodes (the scanner tries the 12- and the 13-digit form). */
    suspend fun findByBarcodes(barcodes: List<String>): Food?

    suspend fun portions(food: Food): List<FoodPortion>

    /** Build facts (sources, dates, counts) for "Über die App". */
    suspend fun meta(): Map<String, String>
}
