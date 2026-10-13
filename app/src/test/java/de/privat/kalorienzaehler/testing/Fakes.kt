package de.privat.kalorienzaehler.testing

import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The "today" of all UI tests: Tuesday, 13 October 2026. */
val TEST_TODAY: LocalDate = LocalDate.of(2026, 10, 13)

val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC)

@Singleton
class FakeProfileRepository @Inject constructor() : ProfileRepository {

    private val current = MutableStateFlow<UserProfile?>(null)
    var failSaves = false
    val saveCount: Int get() = saves

    private var saves = 0

    override val profile: Flow<UserProfile?> = current

    /** The profile as stored right now (for assertions). */
    val stored: UserProfile? get() = current.value

    fun preset(profile: UserProfile?) {
        current.value = profile
    }

    override suspend fun save(profile: UserProfile) {
        if (failSaves) throw IOException("disk full")
        saves++
        current.value = profile
    }

    override suspend fun clear() {
        current.value = null
    }
}

@Singleton
class FakeWeightRepository @Inject constructor() : WeightRepository {

    private val entries = MutableStateFlow<Map<LocalDate, Double>>(emptyMap())
    var failWrites = false

    /** Entries as stored right now, oldest first (for assertions). */
    val stored: List<WeightPoint> get() = entries.value.toSortedMap().map { WeightPoint(it.key, it.value) }

    fun preset(vararg points: WeightPoint) {
        entries.value = points.associate { it.date to it.kg }
    }

    override fun observeEntries(): Flow<List<WeightPoint>> =
        entries.map { map -> map.toSortedMap().map { WeightPoint(it.key, it.value) } }

    override fun observeLatest(): Flow<WeightPoint?> = observeEntries().map { it.lastOrNull() }

    override suspend fun upsert(date: LocalDate, kg: Double) {
        if (failWrites) throw IOException("disk full")
        entries.value = entries.value + (date to kg)
    }

    override suspend fun delete(date: LocalDate) {
        entries.value = entries.value - date
    }
}
