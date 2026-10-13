package de.privat.kalorienzaehler.data.userdb.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One logged food. Nutrition values are snapshots taken when the entry was logged, so history never
 * changes when the food database is updated. Macros are nullable: a quick entry may only know kcal,
 * and missing values are shown as "–", never guessed.
 */
@Entity(
    tableName = "diary_entry",
    indices = [Index(value = ["date", "meal"])],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val meal: Meal,
    @ColumnInfo(name = "food_source") val foodSource: FoodSource,
    @ColumnInfo(name = "food_ref") val foodRef: String?,
    @ColumnInfo(name = "name_snapshot") val nameSnapshot: String,
    @ColumnInfo(name = "brand_snapshot") val brandSnapshot: String?,
    val amount: Double,
    /** What [amount] is measured in: "g", "ml", "Portion" or a portion label. */
    val unit: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
