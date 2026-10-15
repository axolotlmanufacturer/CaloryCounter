package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.fooddb.FoodDao
import de.privat.kalorienzaehler.data.fooddb.FoodDbContentGuard
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.data.fooddb.toDomain
import de.privat.kalorienzaehler.di.ApplicationScope
import de.privat.kalorienzaehler.domain.diary.Food
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodPortion
import de.privat.kalorienzaehler.domain.diary.FoodSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class RoomFoodRepository @Inject constructor(
    private val dao: FoodDao,
    @ApplicationScope private val scope: CoroutineScope,
    private val contentGuard: FoodDbContentGuard,
) : FoodRepository {

    private val mutableStatus = MutableStateFlow<FoodDbStatus>(FoodDbStatus.Preparing)
    override val status: StateFlow<FoodDbStatus> = mutableStatus

    private val lock = Any()
    private var started = false
    private var running = false

    /**
     * Opens the database in the background. The first call starts it; a later call starts it again only after it
     * failed (e.g. no free storage), so reopening the add screen after making room works without restarting the app.
     */
    override fun prepare() = start(retryAfterFailure = true)

    private fun start(retryAfterFailure: Boolean) {
        synchronized(lock) {
            val allowed = !started || (retryAfterFailure && mutableStatus.value == FoodDbStatus.Failed)
            if (running || !allowed) return
            started = true
            running = true
            mutableStatus.value = FoodDbStatus.Preparing
        }
        scope.launch(Dispatchers.IO) {
            val result = try {
                contentGuard.discardStaleCopy()
                // The first query makes Room copy the asset and validate it against the schema.
                dao.count()
                contentGuard.markCurrent()
                FoodDbStatus.Ready
            } catch (cancelled: CancellationException) {
                synchronized(lock) { running = false }
                throw cancelled
            } catch (_: Exception) {
                FoodDbStatus.Failed
            }
            synchronized(lock) { running = false }
            mutableStatus.value = result
        }
    }

    /** Waits for the database; `false` if it cannot be used. */
    private suspend fun ready(): Boolean {
        start(retryAfterFailure = false)
        return mutableStatus.first { it != FoodDbStatus.Preparing } == FoodDbStatus.Ready
    }

    override suspend fun searchBls(match: String, prefix: String, limit: Int): List<Food> =
        if (ready()) dao.searchBls(match, prefix, limit).map { it.toDomain() } else emptyList()

    override suspend fun searchOff(match: String, limit: Int): List<Food> =
        if (ready()) dao.searchOff(match, limit).map { it.toDomain() } else emptyList()

    override suspend fun get(key: FoodKey): Food? =
        if (ready()) dao.find(key.source, key.ref)?.toDomain() else null

    override suspend fun getAll(source: FoodSource, refs: List<String>): List<Food> =
        if (refs.isEmpty() || !ready()) emptyList() else refs.chunked(SQLITE_VARIABLE_LIMIT).flatMap { chunk ->
            dao.findAll(source, chunk).map { it.toDomain() }
        }

    override suspend fun findByBarcodes(barcodes: List<String>): Food? =
        if (barcodes.isEmpty() || !ready()) null else dao.findByBarcodes(barcodes)?.toDomain()

    override suspend fun portions(food: Food): List<FoodPortion> =
        if (ready()) dao.portions(food.id).map { it.toDomain() } else emptyList()

    override suspend fun meta(): Map<String, String> =
        if (ready()) dao.meta().associate { it.key to it.value } else emptyMap()

    private companion object {
        const val SQLITE_VARIABLE_LIMIT = 500
    }
}
