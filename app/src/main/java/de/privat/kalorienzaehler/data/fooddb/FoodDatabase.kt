package de.privat.kalorienzaehler.data.fooddb

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.PerUnit

/**
 * The read-only food database `foods.db` (BLS + Open Food Facts), built by `tools/food-db-builder` and
 * copied from the assets on first use. Nothing writes to it; a new version of the asset simply replaces
 * the copy (there is no user data in it, so a destructive migration is exactly right here).
 *
 * The builder creates the tables from the `createSql` of the exported schema, so changing an entity here
 * means: bump [VERSION], rebuild the database, commit the new schema file.
 */
@Database(
    entities = [FoodEntity::class, FoodFtsEntity::class, FoodPortionEntity::class, FoodMetaEntity::class],
    version = FoodDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(FoodConverters::class)
abstract class FoodDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao

    companion object {
        const val NAME = "foods.db"
        const val ASSET_PATH = "databases/foods.db"
        const val VERSION = 1
    }
}

class FoodConverters {
    @TypeConverter fun sourceToText(source: FoodSource): String = source.name

    @TypeConverter fun textToSource(text: String): FoodSource = FoodSource.valueOf(text)

    @TypeConverter fun unitToText(unit: PerUnit): String = unit.code

    @TypeConverter fun textToUnit(text: String): PerUnit = PerUnit.fromCode(text)
}
