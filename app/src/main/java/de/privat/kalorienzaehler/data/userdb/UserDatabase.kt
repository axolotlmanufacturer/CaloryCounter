package de.privat.kalorienzaehler.data.userdb

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.privat.kalorienzaehler.data.userdb.entity.CustomFoodEntity
import de.privat.kalorienzaehler.data.userdb.entity.DiaryEntryEntity
import de.privat.kalorienzaehler.data.userdb.entity.FavoriteEntity
import de.privat.kalorienzaehler.data.userdb.entity.WeightEntryEntity

/**
 * The read-write database `user.db` (PROMPT.md §5.3). The full schema exists from version 1; DAOs are
 * added together with the features that use them. Schemas are exported to `app/schemas`; any change
 * needs a version bump and an explicit migration (never a destructive fallback).
 */
@Database(
    entities = [
        DiaryEntryEntity::class,
        CustomFoodEntity::class,
        FavoriteEntity::class,
        WeightEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract fun weightDao(): WeightDao

    abstract fun diaryDao(): DiaryDao

    abstract fun customFoodDao(): CustomFoodDao

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val NAME = "user.db"
    }
}
