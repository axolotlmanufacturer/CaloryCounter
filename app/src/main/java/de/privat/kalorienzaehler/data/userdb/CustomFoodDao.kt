package de.privat.kalorienzaehler.data.userdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.privat.kalorienzaehler.data.userdb.entity.CustomFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {

    /** In creation order; the repository sorts alphabetically, because SQLite's NOCASE only folds ASCII. */
    @Query("SELECT * FROM custom_food ORDER BY id")
    fun observeAll(): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_food WHERE id = :id")
    suspend fun get(id: Long): CustomFoodEntity?

    @Query("SELECT * FROM custom_food WHERE barcode = :barcode ORDER BY id LIMIT 1")
    suspend fun findByBarcode(barcode: String): CustomFoodEntity?

    /** The first food (oldest) with one of these spellings of a barcode (EAN-8, UPC-A, EAN-13, GTIN-14). */
    @Query("SELECT * FROM custom_food WHERE barcode IN (:barcodes) ORDER BY id LIMIT 1")
    suspend fun findByBarcodes(barcodes: List<String>): CustomFoodEntity?

    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Update
    suspend fun update(food: CustomFoodEntity)

    @Query("DELETE FROM custom_food WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_food")
    suspend fun deleteEverything()
}
