package de.privat.kalorienzaehler.data.userdb.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import de.privat.kalorienzaehler.domain.diary.FoodSource

@Entity(
    tableName = "favorite",
    primaryKeys = ["food_source", "food_ref"],
)
data class FavoriteEntity(
    @ColumnInfo(name = "food_source") val foodSource: FoodSource,
    @ColumnInfo(name = "food_ref") val foodRef: String,
)
