package de.privat.kalorienzaehler.data.fooddb

import androidx.room.Dao
import androidx.room.Query
import de.privat.kalorienzaehler.domain.diary.FoodSource

@Dao
interface FoodDao {

    /**
     * BLS foods matching the full-text [match] expression (e.g. `kase* gouda*`), best first: names that
     * start with [prefix] (the normalized query), then shorter names. The caller re-ranks.
     */
    @Query(
        """
        SELECT foods.* FROM foods JOIN foods_fts ON foods.id = foods_fts.docid
        WHERE foods_fts MATCH :match AND foods.source = 'BLS'
        ORDER BY (foods.search_text LIKE :prefix || '%') DESC, LENGTH(foods.name), foods.id
        LIMIT :limit
        """,
    )
    suspend fun searchBls(match: String, prefix: String, limit: Int): List<FoodEntity>

    /** Open Food Facts matches; ids are assigned by falling popularity, so id order is popularity order. */
    @Query(
        """
        SELECT foods.* FROM foods JOIN foods_fts ON foods.id = foods_fts.docid
        WHERE foods_fts MATCH :match AND foods.source = 'OFF'
        ORDER BY foods.id
        LIMIT :limit
        """,
    )
    suspend fun searchOff(match: String, limit: Int): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE source = :source AND source_id = :sourceId")
    suspend fun find(source: FoodSource, sourceId: String): FoodEntity?

    /** Several foods of one source at once (favorites, recently logged foods). */
    @Query("SELECT * FROM foods WHERE source = :source AND source_id IN (:sourceIds)")
    suspend fun findAll(source: FoodSource, sourceIds: List<String>): List<FoodEntity>

    /** Foods with one of the given barcodes (the scanner tries 12- and 13-digit forms), best first. */
    @Query("SELECT * FROM foods WHERE barcode IN (:barcodes) ORDER BY id LIMIT 1")
    suspend fun findByBarcodes(barcodes: List<String>): FoodEntity?

    @Query("SELECT * FROM food_portions WHERE food_id = :foodId ORDER BY grams, label")
    suspend fun portions(foodId: Long): List<FoodPortionEntity>

    @Query("SELECT * FROM food_db_meta")
    suspend fun meta(): List<FoodMetaEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}
