package de.privat.kalorienzaehler.data.fooddb

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.PerUnit

/**
 * One food of the bundled database (PROMPT.md section 5.1). All nutrients are per 100 g or per 100 ml
 * ([perUnit]); `null` means the source does not state the value. The builder assigns the ids: BLS
 * foods first, then Open Food Facts foods by falling popularity, so ordering by id is ordering by
 * relevance without sorting.
 */
@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["source", "source_id"], unique = true),
    ],
)
data class FoodEntity(
    @PrimaryKey val id: Long,
    /** BLS or OFF. */
    val source: FoodSource,
    /** The key in the source: BLS code, or the Open Food Facts barcode. Stable when the database is rebuilt. */
    @ColumnInfo(name = "source_id") val sourceId: String,
    val barcode: String?,
    val name: String,
    val brand: String?,
    /** The normalized (PROMPT.md section 5.2) name and brand, which the full-text index searches. */
    @ColumnInfo(name = "search_text") val searchText: String,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val sugar: Double?,
    val fat: Double?,
    val fiber: Double?,
    val salt: Double?,
    @ColumnInfo(name = "per_unit") val perUnit: PerUnit,
    @ColumnInfo(name = "serving_g") val servingG: Double?,
    @ColumnInfo(name = "serving_label") val servingLabel: String?,
    val popularity: Int,
)

/** Full-text index over the normalized text of [FoodEntity]; prefix indexes keep 2-3 letter prefixes fast. */
@Fts4(contentEntity = FoodEntity::class, tokenizer = FtsOptions.TOKENIZER_SIMPLE, prefix = [2, 3])
@Entity(tableName = "foods_fts")
data class FoodFtsEntity(
    @ColumnInfo(name = "search_text") val searchText: String,
)

/** A typical portion of a food ("1 Stück (mittel)" = 150 g). Weights that are estimates say so. */
@Entity(tableName = "food_portions", primaryKeys = ["food_id", "label"])
data class FoodPortionEntity(
    @ColumnInfo(name = "food_id") val foodId: Long,
    val label: String,
    val grams: Double,
    val estimated: Boolean,
)

/** Facts about the build (sources, dates, counts), shown in "Über die App". */
@Entity(tableName = "food_db_meta")
data class FoodMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
