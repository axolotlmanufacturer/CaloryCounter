package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.userdb.WeightDao
import de.privat.kalorienzaehler.data.userdb.entity.WeightEntryEntity
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWeightRepository @Inject constructor(
    private val dao: WeightDao,
) : WeightRepository {

    override fun observeEntries(): Flow<List<WeightPoint>> =
        dao.observeAll().map { entries -> entries.map { it.toPoint() } }

    override fun observeLatest(): Flow<WeightPoint?> = dao.observeLatest().map { it?.toPoint() }

    override suspend fun upsert(date: LocalDate, kg: Double) {
        // Keep an existing note when only the value is corrected.
        dao.upsert(WeightEntryEntity(date = date, kg = kg))
    }

    override suspend fun delete(date: LocalDate) = dao.delete(date)

    private fun WeightEntryEntity.toPoint() = WeightPoint(date, kg)
}
