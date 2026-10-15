package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.runtime.derivedStateOf
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
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.CustomFoodDraft
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.ui.navigation.CustomFoodRoute
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface CustomFoodEvent {
    /** The food was stored; [isNew] says whether it was created (rather than changed). */
    data class Saved(val foodId: Long, val isNew: Boolean) : CustomFoodEvent

    data object Deleted : CustomFoodEvent

    data object Failed : CustomFoodEvent
}

/** Create, change or delete a custom food (values per 100 g/ml as printed on the pack). */
@HiltViewModel(assistedFactory = CustomFoodViewModel.Factory::class)
class CustomFoodViewModel @AssistedInject constructor(
    @Assisted private val route: CustomFoodRoute,
    private val foods: CustomFoodRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: CustomFoodRoute): CustomFoodViewModel
    }

    val isEditing: Boolean = route.customFoodId != null

    var isLoaded by mutableStateOf(!isEditing)
        private set

    var notFound by mutableStateOf(false)
        private set

    // A new food that comes from a scan that found nothing starts with the scanned code.
    var draft by mutableStateOf(CustomFoodDraft(barcode = route.barcode.orEmpty()))
        private set

    val evaluation by derivedStateOf { draft.evaluate() }

    val canSave: Boolean by derivedStateOf { isLoaded && !notFound && !busy && evaluation.isValid }

    private var busy by mutableStateOf(false)
    private var existing: CustomFood? = null

    private val eventChannel = Channel<CustomFoodEvent>(Channel.BUFFERED)
    val events: Flow<CustomFoodEvent> = eventChannel.receiveAsFlow()

    init {
        route.customFoodId?.let { id ->
            viewModelScope.launch {
                val food = foods.get(id)
                if (food == null) {
                    notFound = true
                } else {
                    existing = food
                    draft = CustomFoodDraft.from(food)
                }
                isLoaded = true
            }
        }
    }

    fun edit(transform: (CustomFoodDraft) -> CustomFoodDraft) {
        if (isLoaded && !notFound) draft = transform(draft)
    }

    fun selectPerUnit(unit: PerUnit) = edit { it.copy(perUnit = unit) }

    fun save() {
        val input = evaluation.input ?: return
        if (!canSave) return
        busy = true
        viewModelScope.launch {
            try {
                val now = clock.millis()
                val stored = existing
                val food = CustomFood(
                    id = stored?.id ?: 0L,
                    barcode = input.barcode,
                    name = input.name,
                    brand = input.brand,
                    nutrients = input.nutrients,
                    perUnit = input.perUnit,
                    servingG = input.servingG,
                    createdAtMillis = stored?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                )
                val id = foods.save(food)
                eventChannel.send(CustomFoodEvent.Saved(id, isNew = stored == null))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                busy = false
                eventChannel.send(CustomFoodEvent.Failed)
            }
        }
    }

    /** Deletes the food. Entries already logged keep their own copy of the values. */
    fun delete() {
        val stored = existing ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                foods.delete(stored.id)
                eventChannel.send(CustomFoodEvent.Deleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                busy = false
                eventChannel.send(CustomFoodEvent.Failed)
            }
        }
    }

    /** The name shown in the delete confirmation. */
    val storedName: String? get() = existing?.name
}
