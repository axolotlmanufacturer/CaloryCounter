package de.privat.kalorienzaehler.data.fooddb

import androidx.room.Dao
import androidx.room.Query
import de.privat.kalorienzaehler.domain.diary.FoodSource

@Dao
interface FoodDao {

    /**
     * BLS foods matching the full-text [match] expression (e.g. `kase* gouda*`), best first: names that
     * start with [prefix] (the normalized query), then shorter names. The caller re-ranks.
     *
     * BLS rows have the lowest ids (`food_db_meta.bls_count` of them), so the full-text index only has to look at
     * `docid <= bls_count`: a two-letter prefix like `ka*` matches ten thousand Open Food Facts rows that would
     * otherwise be fetched one by one just to be thrown away. Without the meta row the range is unbounded (slower,
     * same result); the `source` test keeps the result right either way.
     */
    @Query(
        """
        SELECT foods.* FROM foods_fts JOIN foods ON foods.id = foods_fts.docid
        WHERE foods_fts MATCH :match
          AND foods_fts.docid <= COALESCE((SELECT CAST(value AS INTEGER) FROM food_db_meta WHERE key = 'bls_count'), 9223372036854775807)
          AND foods.source = 'BLS'
        ORDER BY (foods.search_text LIKE :prefix || '%') DESC, LENGTH(foods.name), foods.id
        LIMIT :limit
        """,
    )
    suspend fun searchBls(match: String, prefix: String, limit: Int): List<FoodEntity>

    /**
     * Open Food Facts matches. Ids are assigned by falling popularity after the BLS rows, so docid order is
     * popularity order, which the full-text index delivers without sorting; it stops after [limit] rows and skips the
     * BLS range (see [searchBls]).
     */
    @Query(
        """
        SELECT foods.* FROM foods_fts JOIN foods ON foods.id = foods_fts.docid
        WHERE foods_fts MATCH :match
          AND foods_fts.docid > COALESCE((SELECT CAST(value AS INTEGER) FROM food_db_meta WHERE key = 'bls_count'), 0)
          AND foods.source = 'OFF'
        ORDER BY foods_fts.docid
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
