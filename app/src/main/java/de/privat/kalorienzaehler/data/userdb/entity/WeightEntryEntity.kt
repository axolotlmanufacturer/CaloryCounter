package de.privat.kalorienzaehler.data.userdb.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** At most one weight per day; the date is the key, so saving again the same day replaces the value. */
@Entity(tableName = "weight_entry")
data class WeightEntryEntity(
    @PrimaryKey val date: LocalDate,
    val kg: Double,
    val note: String? = null,
)
