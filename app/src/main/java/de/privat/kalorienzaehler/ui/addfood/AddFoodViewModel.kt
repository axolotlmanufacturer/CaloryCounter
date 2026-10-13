package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.repository.CustomFoodRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.text.TextNormalizer
import de.privat.kalorienzaehler.ui.navigation.AddFoodRoute
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The chips above the list. "Favoriten" joins them together with the food database. */
enum class AddFilter { RECENT, CUSTOM }

/** What the list shows: the recent foods, all own foods, or the matches for the typed text. */
enum class AddMode { RECENT, CUSTOM, SEARCH }

data class AddFoodState(
    val mode: AddMode,
    val foods: List<CustomFood>,
    val energyUnit: EnergyUnit,
) {
    /** The chip to highlight; none while a search is running. */
    val selectedFilter: AddFilter?
        get() = when (mode) {
            AddMode.RECENT -> AddFilter.RECENT
            AddMode.CUSTOM -> AddFilter.CUSTOM
            AddMode.SEARCH -> null
        }
}

/**
 * Picking a food for one meal of one day. For now the foods are the user's own; the food database
 * (search results, favorites) is added next to them in the phase that builds it.
 */
@HiltViewModel(assistedFactory = AddFoodViewModel.Factory::class)
class AddFoodViewModel @AssistedInject constructor(
    @Assisted route: AddFoodRoute,
    customFoods: CustomFoodRepository,
    profiles: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: AddFoodRoute): AddFoodViewModel
    }

    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: Meal = route.meal
    val today: LocalDate get() = LocalDate.now(clock)

    /** The text in the search field (snapshot state, so the field never lags behind the keyboard). */
    var query by mutableStateOf("")
        private set

    // The same text and the chosen chip as flows, which drive the list.
    private val queryFlow = MutableStateFlow("")
    private val chosenFilter = MutableStateFlow<AddFilter?>(null)

    /** `null` until the first data has arrived. */
    val state: StateFlow<AddFoodState?> = combine(
        customFoods.observeAll(),
        customFoods.observeRecent(since = today.minusDays(RECENT_DAYS), limit = RECENT_LIMIT),
        profiles.profile.map { it?.energyUnit ?: EnergyUnit.KCAL },
        queryFlow,
        chosenFilter,
    ) { all, recent, unit, text, filter ->
        when {
            text.isNotBlank() -> AddFoodState(
                mode = AddMode.SEARCH,
                foods = all.filter { TextNormalizer.matchesPrefixes(it.searchText(), text) },
                energyUnit = unit,
            )
            filter == AddFilter.CUSTOM || (filter == null && recent.isEmpty() && all.isNotEmpty()) ->
                AddFoodState(AddMode.CUSTOM, all, unit)
            else -> AddFoodState(AddMode.RECENT, recent, unit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onQueryChange(text: String) {
        query = text
        queryFlow.value = text
    }

    fun selectFilter(filter: AddFilter) {
        chosenFilter.value = filter
    }

    private fun CustomFood.searchText(): String = listOfNotNull(name, brand, barcode).joinToString(" ")

    private companion object {
        const val RECENT_DAYS = 30L
        const val RECENT_LIMIT = 30
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
