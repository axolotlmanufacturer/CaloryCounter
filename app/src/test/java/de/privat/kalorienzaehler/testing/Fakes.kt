package de.privat.kalorienzaehler.testing

import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.data.repository.CustomFoodRepository
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.data.repository.FavoriteRepository
import de.privat.kalorienzaehler.data.repository.FoodRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Food
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodPortion
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.model.UserProfile
import de.privat.kalorienzaehler.domain.text.TextNormalizer
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

@Singleton
class FakeDiaryRepository @Inject constructor() : DiaryRepository {

    private val entries = MutableStateFlow<Map<Long, DiaryEntry>>(emptyMap())
    private var nextId = 1L
    var failWrites = false

    /** Every stored entry, oldest first (for assertions). */
    val stored: List<DiaryEntry> get() = entries.value.values.sortedWith(ORDER)

    /** Puts entries into the diary; entries with id 0 get a fresh one. Returns them as stored. */
    fun preset(vararg presets: DiaryEntry): List<DiaryEntry> = presets.map { entry ->
        val withId = if (entry.id == 0L) entry.copy(id = nextId++) else entry.also { nextId = maxOf(nextId, it.id + 1) }
        entries.value = entries.value + (withId.id to withId)
        withId
    }

    override fun observeDay(date: LocalDate): Flow<List<DiaryEntry>> =
        entries.map { all -> all.values.filter { it.date == date }.sortedWith(ORDER) }

    /** Like the real query: foods (no quick entries) logged since [since], the most often logged first. */
    override fun observeRecentFoods(since: LocalDate, limit: Int): Flow<List<FoodKey>> = entries.map { all ->
        all.values
            .filter { it.source != FoodSource.QUICK && it.foodRef != null && !it.date.isBefore(since) }
            .groupBy { FoodKey(it.source, it.foodRef!!) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<FoodKey, List<DiaryEntry>>> { it.value.size }
                    .thenByDescending { row -> row.value.maxOf { it.createdAtMillis } }
                    .thenByDescending { row -> row.value.maxOf { it.id } },
            )
            .take(limit)
            .map { it.key }
    }

    /** Like the real query: the food logged last first. */
    override fun observeLastLoggedFoods(since: LocalDate, limit: Int): Flow<List<FoodKey>> = entries.map { all ->
        all.values
            .filter { it.source != FoodSource.QUICK && it.foodRef != null && !it.date.isBefore(since) }
            .groupBy { FoodKey(it.source, it.foodRef!!) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<FoodKey, List<DiaryEntry>>> { row -> row.value.maxOf { it.createdAtMillis } }
                    .thenByDescending { row -> row.value.maxOf { it.id } },
            )
            .take(limit)
            .map { it.key }
    }

    override suspend fun get(id: Long): DiaryEntry? = entries.value[id]

    override suspend fun add(entry: DiaryEntry): Long {
        if (failWrites) throw IOException("disk full")
        val id = nextId++
        entries.value = entries.value + (id to entry.copy(id = id))
        return id
    }

    override suspend fun update(entry: DiaryEntry) {
        if (failWrites) throw IOException("disk full")
        entries.value = entries.value + (entry.id to entry)
    }

    override suspend fun delete(id: Long) {
        entries.value = entries.value - id
    }

    override suspend fun restore(entry: DiaryEntry) {
        entries.value = entries.value + (entry.id to entry)
    }

    override suspend fun copyMeal(from: LocalDate, to: LocalDate, meal: Meal, createdAtMillis: Long): List<Long> {
        if (failWrites) throw IOException("disk full")
        // Like the real repository: only an empty meal is filled.
        if (entries.value.values.any { it.date == to && it.meal == meal }) return emptyList()
        val copies = entries.value.values
            .filter { it.date == from && it.meal == meal }
            .sortedWith(ORDER)
            .map { it.copiedTo(to, createdAtMillis) }
        return copies.map { copy -> add(copy) }
    }

    override suspend fun deleteAll(ids: List<Long>) {
        entries.value = entries.value - ids.toSet()
    }

    override suspend fun deleteEverything() {
        entries.value = emptyMap()
    }

    private companion object {
        val ORDER = compareBy<DiaryEntry>({ it.createdAtMillis }, { it.id })
    }
}

@Singleton
class FakeCustomFoodRepository @Inject constructor() : CustomFoodRepository {

    private val foods = MutableStateFlow<Map<Long, CustomFood>>(emptyMap())
    private var nextId = 1L
    var failWrites = false

    val stored: List<CustomFood> get() = foods.value.values.sortedBy { it.id }

    /** Puts foods into the repository; foods with id 0 get a fresh one. Returns them as stored. */
    fun preset(vararg presets: CustomFood): List<CustomFood> = presets.map { food ->
        val withId = if (food.id == 0L) food.copy(id = nextId++) else food.also { nextId = maxOf(nextId, it.id + 1) }
        foods.value = foods.value + (withId.id to withId)
        withId
    }

    override fun observeAll(): Flow<List<CustomFood>> =
        foods.map { all -> all.values.sortedWith(compareBy({ TextNormalizer.normalize(it.name) }, { it.id })) }

    override suspend fun get(id: Long): CustomFood? = foods.value[id]

    override suspend fun findByBarcodes(barcodes: List<String>): CustomFood? =
        foods.value.values.sortedBy { it.id }.firstOrNull { it.barcode in barcodes }

    override suspend fun save(food: CustomFood): Long {
        if (failWrites) throw IOException("disk full")
        val id = if (food.id == 0L) nextId++ else food.id
        foods.value = foods.value + (id to food.copy(id = id))
        return id
    }

    override suspend fun delete(id: Long) {
        foods.value = foods.value - id
    }

    override suspend fun deleteEverything() {
        foods.value = emptyMap()
    }
}

/** The bundled food database as a list in memory; [status] can be set to test "preparing" and "failed". */
@Singleton
class FakeFoodRepository @Inject constructor() : FoodRepository {

    private val mutableStatus = MutableStateFlow<FoodDbStatus>(FoodDbStatus.Ready)
    private val foods = mutableListOf<Food>()
    private val portionsByFood = mutableMapOf<String, List<FoodPortion>>()
    var prepareCalls = 0
        private set

    /** How many full-text searches ran (to test the debounce). */
    var searchCalls = 0
        private set

    override val status: StateFlow<FoodDbStatus> = mutableStatus

    fun setStatus(status: FoodDbStatus) {
        mutableStatus.value = status
    }

    /** Adds foods in relevance order (like the real database, `id` is the relevance within a source). */
    fun preset(vararg presets: Food) {
        foods += presets
    }

    fun presetPortions(food: Food, vararg portions: FoodPortion) {
        portionsByFood["${food.key.source}:${food.key.ref}"] = portions.toList()
    }

    override fun prepare() {
        prepareCalls++
    }

    override suspend fun searchBls(match: String, prefix: String, limit: Int): List<Food> =
        search(FoodSource.BLS, match, limit)

    override suspend fun searchOff(match: String, limit: Int): List<Food> =
        search(FoodSource.OFF, match, limit).sortedByDescending { it.popularity }

    private fun search(source: FoodSource, match: String, limit: Int): List<Food> {
        if (source == FoodSource.BLS) searchCalls++
        if (mutableStatus.value != FoodDbStatus.Ready) return emptyList()
        // "kase* gouda*": every word must start a word of the name or brand.
        val prefixes = match.split(' ').filter { it.isNotBlank() }.map { it.removeSuffix("*") }
        return foods
            .filter { it.key.source == source }
            .filter { food ->
                val words = TextNormalizer.normalize("${food.name} ${food.brand.orEmpty()}").split(' ')
                prefixes.all { prefix -> words.any { it.startsWith(prefix) } }
            }
            .take(limit)
    }

    /** Like the real repository: lookups wait while the database is being prepared, and find nothing if it failed. */
    private suspend fun ready(): Boolean = mutableStatus.first { it != FoodDbStatus.Preparing } == FoodDbStatus.Ready

    override suspend fun get(key: FoodKey): Food? = if (ready()) foods.firstOrNull { it.key == key } else null

    override suspend fun getAll(source: FoodSource, refs: List<String>): List<Food> =
        if (refs.isEmpty() || !ready()) emptyList() else foods.filter { it.key.source == source && it.key.ref in refs }

    override suspend fun findByBarcodes(barcodes: List<String>): Food? =
        if (barcodes.isEmpty() || !ready()) null else foods.firstOrNull { it.barcode in barcodes }

    override suspend fun portions(food: Food): List<FoodPortion> =
        if (ready()) portionsByFood["${food.key.source}:${food.key.ref}"].orEmpty() else emptyList()

    override suspend fun meta(): Map<String, String> = emptyMap()
}

@Singleton
class FakeFavoriteRepository @Inject constructor() : FavoriteRepository {

    private val keys = MutableStateFlow<Set<FoodKey>>(emptySet())

    val stored: Set<FoodKey> get() = keys.value

    fun preset(vararg presets: FoodKey) {
        keys.value = keys.value + presets
    }

    override fun observeAll(): Flow<List<FoodKey>> = keys.map { it.toList() }

    override fun observeIsFavorite(key: FoodKey): Flow<Boolean> = keys.map { key in it }

    override suspend fun setFavorite(key: FoodKey, favorite: Boolean) {
        keys.value = if (favorite) keys.value + key else keys.value - key
    }

    override suspend fun deleteEverything() {
        keys.value = emptySet()
    }
}
