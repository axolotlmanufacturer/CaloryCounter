package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
        combine(diary.observeRecentFoods(since, limit), custom.observeAll()) { keys, _ -> resolve(keys) }

    override fun observeFavorites(): Flow<List<FoodHit>> =
        combine(favorites.observeAll(), custom.observeAll()) { keys, _ ->
            resolve(keys).sortedWith(compareBy({ TextNormalizer.normalize(it.name) }, { it.key.ref }))
        }

    override suspend fun search(query: String, today: LocalDate): List<FoodHit> {
        val match = FoodSearch.matchExpression(query) ?: return emptyList()
        val prefix = FoodSearch.normalizedPrefix(query)

        // Tier 1: what the user logged lately and the own foods, matching the query.
        val recent = resolve(diary.observeRecentFoods(today.minusDays(RECENT_DAYS), RECENT_LIMIT).first())
            .filter { matches(it, query) }
        // Own foods are also found by their barcode ("4006381" finds the pack typed in by hand).
        val ownFoods = custom.observeAll().first()
            .filter { TextNormalizer.matchesPrefixes("${it.name} ${it.brand.orEmpty()} ${it.barcode.orEmpty()}", query) }
            .map { it.toHit() }
        val personal = recent + ownFoods

        // While the database is still being unpacked the own foods answer alone; the search runs again when it is ready.
        val dbReady = foods.status.value == FoodDbStatus.Ready
        val bls = if (dbReady) foods.searchBls(match, prefix, CANDIDATES).map { it.toHit() } else emptyList()
        val off = if (dbReady) foods.searchOff(match, CANDIDATES).map { it.toHit() } else emptyList()
        return FoodSearch.rank(query, personal, bls, off)
    }

    override suspend fun load(key: FoodKey): LoggableFood? = when (key.source) {
        FoodSource.CUSTOM -> key.ref.toLongOrNull()?.let { custom.get(it) }?.toLoggable()
        FoodSource.BLS, FoodSource.OFF -> foods.get(key)?.let { food -> food.toLoggable(foods.portions(food)) }
        FoodSource.QUICK -> null
    }

    /** The foods behind these keys, in the order of the keys; keys whose food is gone are left out. */
    private suspend fun resolve(keys: List<FoodKey>): List<FoodHit> {
        if (keys.isEmpty()) return emptyList()
        val customFoods = if (keys.any { it.source == FoodSource.CUSTOM }) {
            custom.observeAll().first().associateBy { it.id.toString() }
        } else {
            emptyMap()
        }
        val dbFoods = listOf(FoodSource.BLS, FoodSource.OFF).associateWith { source ->
            val refs = keys.filter { it.source == source }.map { it.ref }
            foods.getAll(source, refs).associateBy { it.key.ref }
        }
        return keys.mapNotNull { key ->
            when (key.source) {
                FoodSource.CUSTOM -> customFoods[key.ref]?.toHit()
                FoodSource.BLS, FoodSource.OFF -> dbFoods[key.source]?.get(key.ref)?.toHit()
                FoodSource.QUICK -> null
            }
        }
    }

    private fun matches(hit: FoodHit, query: String): Boolean =
        TextNormalizer.matchesPrefixes("${hit.name} ${hit.brand.orEmpty()}", query)

    private companion object {
        const val RECENT_DAYS = 30L
        const val RECENT_LIMIT = 100
        const val CANDIDATES = 120
    }
}
