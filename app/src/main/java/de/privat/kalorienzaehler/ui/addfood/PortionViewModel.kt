package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.data.repository.FavoriteRepository
import de.privat.kalorienzaehler.data.repository.FoodCatalog
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.domain.diary.AmountInput
import de.privat.kalorienzaehler.domain.diary.AmountUnit
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.diary.NutrientsPer100
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.format.GermanFormat
import de.privat.kalorienzaehler.ui.navigation.PortionRoute
import de.privat.kalorienzaehler.ui.navigation.PortionTarget
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The food (or the logged entry) the amount is chosen for. */
data class PortionSubject(
    /** The food, if it can be a favorite; `null` for entries that have no food behind them. */
    val key: FoodKey?,
    val name: String,
    val brand: String?,
    val perUnit: PerUnit,
    val per100: NutrientsPer100,
    /** The ways to state the amount; an existing entry keeps the unit it was logged in. */
    val units: List<AmountUnit>,
    /** Foods show their full Nährwerttabelle; entries only snapshot energy and the three macros. */
    val showTable: Boolean,
)

/**
 * Amount, unit and meal for one food, with a live preview of the energy and macros. Used both to log
 * a food ([PortionTarget.NewFromFood]) and to change a logged entry ([PortionTarget.ExistingEntry]).
 */
@HiltViewModel(assistedFactory = PortionViewModel.Factory::class)
class PortionViewModel @AssistedInject constructor(
    @Assisted private val route: PortionRoute,
    private val catalog: FoodCatalog,
    private val favorites: FavoriteRepository,
    private val diary: DiaryRepository,
    profiles: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: PortionRoute): PortionViewModel
    }

    /** The calendar day "Heute" stands for. */
    val today: LocalDate get() = LocalDate.now(clock)

    val isEditing: Boolean = route.target is PortionTarget.ExistingEntry

    var isLoaded by mutableStateOf(false)
        private set

    /** The food or entry no longer exists (deleted in the meantime). */
    var notFound by mutableStateOf(false)
        private set

    var subject by mutableStateOf<PortionSubject?>(null)
        private set

    var amountText by mutableStateOf("")
        private set

    var unitIndex by mutableIntStateOf(0)
        private set

    var meal by mutableStateOf(Meal.FRUEHSTUECK)
        private set

    var date: LocalDate by mutableStateOf(LocalDate.ofEpochDay(0))
        private set

    var energyUnit by mutableStateOf(EnergyUnit.KCAL)
        private set

    val selectedUnit: AmountUnit? get() = subject?.units?.getOrNull(unitIndex)

    /** Whether the food carries a star; `null` when it cannot (an entry without a food). */
    var isFavorite: Boolean? by mutableStateOf(null)
        private set

    private val parsedAmount by derivedStateOf { AmountInput.parse(amountText) }

    val amountError by derivedStateOf { parsedAmount.error }

    /** Energy and macros for the typed amount; `null` while the amount is not usable. */
    val preview: NutrientAmounts? by derivedStateOf {
        val amount = parsedAmount.amount
        val current = subject
        val unit = current?.units?.getOrNull(unitIndex)
        if (amount == null || current == null || unit == null) null else current.per100.forGrams(unit.toGrams(amount))
    }

    val canSave: Boolean by derivedStateOf { isLoaded && !notFound && !saving && preview != null }

    private var saving by mutableStateOf(false)
    private var existing: DiaryEntry? = null

    private val eventChannel = Channel<FormEvent>(Channel.BUFFERED)
    val events: Flow<FormEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            energyUnit = profiles.profile.first()?.energyUnit ?: EnergyUnit.KCAL
            when (val target = route.target) {
                is PortionTarget.NewFromFood -> loadFood(target)
                is PortionTarget.ExistingEntry -> loadEntry(target)
            }
            isLoaded = true
            subject?.key?.let(::observeFavorite)
        }
    }

    private fun observeFavorite(key: FoodKey) {
        viewModelScope.launch { favorites.observeIsFavorite(key).collect { isFavorite = it } }
    }

    private suspend fun loadFood(target: PortionTarget.NewFromFood) {
        val key = FoodKey(target.source, target.ref)
        // The route knows the meal and the day even if the food is gone: the title bar can still say where it was for.
        meal = target.meal
        date = LocalDate.ofEpochDay(target.epochDay)
        val food = catalog.load(key)
        if (food == null) {
            notFound = true
            return
        }
        val units = food.units()
        subject = PortionSubject(key, food.name, food.brand, food.perUnit, food.nutrients, units, showTable = true)
        // The pack's own portion is the natural first choice; otherwise start at 100 g/ml.
        unitIndex = units.indexOfFirst { !it.isBase && !it.estimated }.takeIf { it >= 0 } ?: 0
        amountText = defaultAmount(units[unitIndex])
    }

    private suspend fun loadEntry(target: PortionTarget.ExistingEntry) {
        val entry = diary.get(target.entryId)
        val per100 = entry?.per100()
        if (entry == null || per100 == null) {
            notFound = true
            return
        }
        existing = entry
        meal = entry.meal
        date = entry.date
        val perUnit = if (entry.unit == PerUnit.MILLILITER.code) PerUnit.MILLILITER else PerUnit.GRAM
        val foodKey = entry.foodRef?.takeIf { entry.source != FoodSource.QUICK }?.let { FoodKey(entry.source, it) }
        val food = foodKey?.let { catalog.load(it) }
        // The unit keeps what it was logged as; whether its weight was an estimate comes from the food, if it is still there.
        val estimated = food?.units()?.firstOrNull { it.key == entry.unit }?.estimated ?: false
        subject = PortionSubject(
            // A food that is gone (deleted own food) cannot be starred: that favorite would be invisible.
            key = foodKey.takeIf { food != null },
            name = entry.name,
            brand = entry.brand,
            perUnit = perUnit,
            per100 = per100,
            units = listOf(AmountUnit.of(entry).copy(estimated = estimated)),
            showTable = false,
        )
        unitIndex = 0
        amountText = GermanFormat.editable(entry.amount, maxFractionDigits = 2)
    }

    fun toggleFavorite() {
        val key = subject?.key ?: return
        val target = !(isFavorite ?: false)
        viewModelScope.launch {
            try {
                favorites.setFavorite(key, target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventChannel.send(FormEvent.FAILED)
            }
        }
    }

    fun onAmountChange(text: String) {
        amountText = text
    }

    fun selectUnit(index: Int) {
        val units = subject?.units ?: return
        if (index !in units.indices || index == unitIndex) return
        unitIndex = index
        amountText = defaultAmount(units[index])
    }

    fun selectMeal(newMeal: Meal) {
        meal = newMeal
    }

    fun save() {
        val amount = parsedAmount.amount ?: return
        val nutrients = preview ?: return
        val unit = selectedUnit ?: return
        val current = subject ?: return
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            try {
                val stored = existing
                if (stored != null) {
                    diary.update(
                        stored.copy(meal = meal, amount = amount, grams = unit.toGrams(amount), nutrients = nutrients),
                    )
                } else {
                    diary.add(
                        DiaryEntry(
                            date = date,
                            meal = meal,
                            source = checkNotNull(current.key).source,
                            foodRef = current.key.ref,
                            name = current.name,
                            brand = current.brand,
                            amount = amount,
                            unit = unit.key,
                            grams = unit.toGrams(amount),
                            nutrients = nutrients,
                            createdAtMillis = clock.millis(),
                        ),
                    )
                }
                eventChannel.send(FormEvent.SAVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saving = false
                eventChannel.send(FormEvent.FAILED)
            }
        }
    }

    private fun defaultAmount(unit: AmountUnit): String = if (unit.isBase) "100" else "1"
}
