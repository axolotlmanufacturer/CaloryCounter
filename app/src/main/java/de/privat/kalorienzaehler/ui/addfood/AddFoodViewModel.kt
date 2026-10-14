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
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.data.repository.FoodCatalog
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.domain.diary.FoodHit
import de.privat.kalorienzaehler.domain.diary.FoodSearch
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.ui.navigation.AddFoodRoute
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The chips above the list (PROMPT.md section 7.3). */
enum class AddFilter { RECENT, FAVORITES, CUSTOM }

/** What the list shows: one of the chips' lists, or the matches for the typed text. */
enum class AddMode { RECENT, FAVORITES, CUSTOM, SEARCH }

data class AddFoodState(
    val mode: AddMode,
    val foods: List<FoodHit>,
    val energyUnit: EnergyUnit,
    val dbStatus: FoodDbStatus,
    /** The typed text has no word long enough to search for. */
    val queryTooShort: Boolean = false,
    /** The typed text is still being searched: [foods] belong to an earlier text (or are empty). */
    val searching: Boolean = false,
) {
    /** The chip to highlight; none while a search is running. */
    val selectedFilter: AddFilter?
        get() = when (mode) {
            AddMode.RECENT -> AddFilter.RECENT
            AddMode.FAVORITES -> AddFilter.FAVORITES
            AddMode.CUSTOM -> AddFilter.CUSTOM
            AddMode.SEARCH -> null
        }
}

/**
 * Picking a food for one meal of one day: search over the user's own foods, the recently logged ones and the
 * bundled database (BLS + Open Food Facts), plus the chips Zuletzt, Favoriten and Eigene.
 */
@HiltViewModel(assistedFactory = AddFoodViewModel.Factory::class)
class AddFoodViewModel @AssistedInject constructor(
    @Assisted route: AddFoodRoute,
    private val catalog: FoodCatalog,
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

    init {
        // Opening the database is the first thing to start: it is copied out of the app package on first use.
        catalog.prepare()
    }

    /** Search results for the typed text: after a short pause in typing, and again when the database becomes ready. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchResults: Flow<SearchResult> = queryFlow
        .debounce { text -> if (text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            catalog.dbStatus.flatMapLatest {
                flow { emit(SearchResult(text, if (text.isBlank()) emptyList() else catalog.search(text, today))) }
            }
        }

    private val lists: Flow<Lists> = combine(
        catalog.observeRecent(today.minusDays(RECENT_DAYS), RECENT_LIMIT),
        catalog.observeFavorites(),
        catalog.observeCustom(),
    ) { recent, favorites, custom -> Lists(recent, favorites, custom) }

    /** `null` until the first data has arrived. */
    val state: StateFlow<AddFoodState?> = combine(
        lists,
        searchResults,
        profiles.profile.map { it?.energyUnit ?: EnergyUnit.KCAL },
        queryFlow,
        combine(chosenFilter, catalog.dbStatus) { filter, status -> filter to status },
    ) { lists, results, unit, text, (filter, status) ->
        when {
            text.isNotBlank() -> AddFoodState(
                mode = AddMode.SEARCH,
                foods = results.foods,
                energyUnit = unit,
                dbStatus = status,
                queryTooShort = FoodSearch.matchExpression(text) == null,
                searching = results.text != text,
            )
            else -> {
                val mode = when (filter ?: defaultFilter(lists)) {
                    AddFilter.RECENT -> AddMode.RECENT
                    AddFilter.FAVORITES -> AddMode.FAVORITES
                    AddFilter.CUSTOM -> AddMode.CUSTOM
                }
                val foods = when (mode) {
                    AddMode.RECENT -> lists.recent
                    AddMode.FAVORITES -> lists.favorites
                    else -> lists.custom
                }
                AddFoodState(mode, foods, unit, status)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onQueryChange(text: String) {
        query = text
        queryFlow.value = text
    }

    /** Choosing a chip leaves the search: the typed text is cleared and the chosen list shows. */
    fun selectFilter(filter: AddFilter) {
        onQueryChange("")
        chosenFilter.value = filter
    }

    /** Before the user chooses: what was eaten lately, else the favorites, else the own foods. */
    private fun defaultFilter(lists: Lists): AddFilter = when {
        lists.recent.isNotEmpty() -> AddFilter.RECENT
        lists.favorites.isNotEmpty() -> AddFilter.FAVORITES
        lists.custom.isNotEmpty() -> AddFilter.CUSTOM
        else -> AddFilter.RECENT
    }

    /** What was found for [text]. */
    private data class SearchResult(val text: String, val foods: List<FoodHit>)

    private data class Lists(val recent: List<FoodHit>, val favorites: List<FoodHit>, val custom: List<FoodHit>)

    private companion object {
        const val RECENT_DAYS = 30L
        const val RECENT_LIMIT = 30
        const val SEARCH_DEBOUNCE_MS = 150L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
