package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.domain.barcode.Barcode
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.FoodHit
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSearch
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.LoggableFood
import de.privat.kalorienzaehler.domain.diary.toHit
import de.privat.kalorienzaehler.domain.diary.toLoggable
import de.privat.kalorienzaehler.domain.text.TextNormalizer
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

@Singleton
class DefaultFoodCatalog @Inject constructor(
    private val foods: FoodRepository,
    private val custom: CustomFoodRepository,
    private val favorites: FavoriteRepository,
    private val diary: DiaryRepository,
) : FoodCatalog {

    override val dbStatus: StateFlow<FoodDbStatus> get() = foods.status

    override fun prepare() = foods.prepare()

    override fun observeCustom(): Flow<List<FoodHit>> = custom.observeAll().map { list -> list.map { it.toHit() } }

    override fun observeRecent(since: LocalDate, limit: Int): Flow<List<FoodHit>> =
        combine(diary.observeLastLoggedFoods(since, limit), custom.observeAll(), foods.status) { keys, customFoods, status ->
            resolve(keys, customFoods, status)
        }

    override fun observeFavorites(): Flow<List<FoodHit>> =
        combine(favorites.observeAll(), custom.observeAll(), foods.status) { keys, customFoods, status ->
            resolve(keys, customFoods, status).sortedWith(compareBy({ TextNormalizer.normalize(it.name) }, { it.key.ref }))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSearch(query: String, today: LocalDate): Flow<List<FoodHit>> {
        val match = FoodSearch.matchExpression(query) ?: return flowOf(emptyList())
        val prefix = FoodSearch.normalizedPrefix(query)
        return combine(
            diary.observeRecentFoods(today.minusDays(RECENT_DAYS), RECENT_LIMIT),
            custom.observeAll(),
            foods.status,
        ) { recentKeys, customFoods, status -> Candidates(recentKeys, customFoods, status) }
            .mapLatest { (recentKeys, customFoods, status) ->
                // Tier 1: what the user logged lately (most often first) and the own foods, matching the query.
                val recent = resolve(recentKeys, customFoods, status).filter { matches(it.name, it.brand, null, query) }
                val own = customFoods
                    .filter { matches(it.name, it.brand, it.barcode, query) } // an own food is also found by its barcode
                    .map { it.toHit() }
                // While the database is still being unpacked the own foods answer alone; the flow runs again when
                // it is ready.
                val dbReady = status == FoodDbStatus.Ready
                val bls = if (dbReady) foods.searchBls(match, prefix, CANDIDATES).map { it.toHit() } else emptyList()
                val off = if (dbReady) foods.searchOff(match, CANDIDATES).map { it.toHit() } else emptyList()
                FoodSearch.rank(query, recent + own, bls, off)
            }
    }

    override suspend fun search(query: String, today: LocalDate): List<FoodHit> = observeSearch(query, today).first()

    override suspend fun findByBarcode(barcode: Barcode): BarcodeLookup {
        custom.findByBarcodes(barcode.lookupCodes)?.let { return BarcodeLookup.Found(it.toHit()) }
        foods.findByBarcodes(barcode.lookupCodes)?.let { return BarcodeLookup.Found(it.toHit()) }
        return BarcodeLookup.NotFound(databaseUnavailable = foods.status.value == FoodDbStatus.Failed)
    }

    override suspend fun load(key: FoodKey): LoggableFood? = when (key.source) {
        FoodSource.CUSTOM -> key.ref.toLongOrNull()?.let { custom.get(it) }?.toLoggable()
        FoodSource.BLS, FoodSource.OFF -> foods.get(key)?.let { food -> food.toLoggable(foods.portions(food)) }
        FoodSource.QUICK -> null
    }

    /**
     * The foods behind these keys, in the order of the keys; keys whose food is gone are left out. Database foods are
     * skipped while the database is not [FoodDbStatus.Ready]: the lists must not wait for the copy of the asset, and
     * they are resolved again when the status changes.
     */
    private suspend fun resolve(keys: List<FoodKey>, customFoods: List<CustomFood>, status: FoodDbStatus): List<FoodHit> {
        if (keys.isEmpty()) return emptyList()
        val customById = customFoods.associateBy { it.id.toString() }
        val dbFoods = listOf(FoodSource.BLS, FoodSource.OFF).associateWith { source ->
            val refs = keys.filter { it.source == source }.map { it.ref }
            if (status == FoodDbStatus.Ready && refs.isNotEmpty()) foods.getAll(source, refs).associateBy { it.key.ref } else emptyMap()
        }
        return keys.mapNotNull { key ->
            when (key.source) {
                FoodSource.CUSTOM -> customById[key.ref]?.toHit()
                FoodSource.BLS, FoodSource.OFF -> dbFoods[key.source]?.get(key.ref)?.toHit()
                FoodSource.QUICK -> null
            }
        }
    }

    private fun matches(name: String, brand: String?, barcode: String?, query: String): Boolean =
        TextNormalizer.matchesPrefixes("$name ${brand.orEmpty()} ${barcode.orEmpty()}", query)

    private data class Candidates(val recentKeys: List<FoodKey>, val customFoods: List<CustomFood>, val status: FoodDbStatus)

    private companion object {
        const val RECENT_DAYS = 30L

        /** Foods of the last 30 days that are considered for tier 1: far more than anyone logs, so the query decides. */
        const val RECENT_LIMIT = 1000
        const val CANDIDATES = 120
    }
}
