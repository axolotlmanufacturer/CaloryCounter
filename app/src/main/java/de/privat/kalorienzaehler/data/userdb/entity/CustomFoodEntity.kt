package de.privat.kalorienzaehler.data.userdb.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.privat.kalorienzaehler.domain.diary.PerUnit

/** A food entered by the user, with values as printed on the pack (per 100 g or 100 ml). */
@Entity(
    tableName = "custom_food",
    indices = [Index(value = ["barcode"])],
)
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val sugar: Double?,
    val fat: Double?,
    val fiber: Double?,
    val salt: Double?,
    @ColumnInfo(name = "per_unit") val perUnit: PerUnit,
    @ColumnInfo(name = "serving_g") val servingG: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
