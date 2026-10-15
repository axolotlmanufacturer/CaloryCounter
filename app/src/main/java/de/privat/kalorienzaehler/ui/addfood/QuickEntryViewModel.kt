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
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.domain.diary.AmountUnit
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.QuickEntryDraft
import de.privat.kalorienzaehler.ui.navigation.QuickEntryRoute
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The Schnelleintrag form: calories, optional macros and a label. The same screen changes an
 * existing quick entry (calories, macros, label and meal; a quick entry has no amount).
 */
@HiltViewModel(assistedFactory = QuickEntryViewModel.Factory::class)
class QuickEntryViewModel @AssistedInject constructor(
    @Assisted private val route: QuickEntryRoute,
    private val diary: DiaryRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: QuickEntryRoute): QuickEntryViewModel
    }

    /** The calendar day "Heute" stands for. */
    val today: LocalDate get() = LocalDate.now(clock)

    val isEditing: Boolean = route.entryId != null

    var isLoaded by mutableStateOf(!isEditing)
        private set

    /** Editing an entry that has been deleted in the meantime (or is not a quick entry). */
    var notFound by mutableStateOf(false)
        private set

    var draft by mutableStateOf(QuickEntryDraft())
        private set

    var meal by mutableStateOf(route.meal)
        private set

    var date: LocalDate by mutableStateOf(LocalDate.ofEpochDay(route.epochDay))
        private set

    val evaluation by derivedStateOf { draft.evaluate() }

    val canSave: Boolean by derivedStateOf { isLoaded && !notFound && !saving && evaluation.isValid }

    private var saving by mutableStateOf(false)
    private var existing: DiaryEntry? = null

    private val eventChannel = Channel<FormEvent>(Channel.BUFFERED)
    val events: Flow<FormEvent> = eventChannel.receiveAsFlow()

    init {
        route.entryId?.let { entryId ->
            viewModelScope.launch {
                val entry = diary.get(entryId)
                if (entry == null || !entry.isQuick) {
                    notFound = true
                } else {
                    existing = entry
                    draft = QuickEntryDraft.from(entry)
                    meal = entry.meal
                    date = entry.date
                }
                isLoaded = true
            }
        }
    }

    fun edit(transform: (QuickEntryDraft) -> QuickEntryDraft) {
        if (isLoaded && !notFound) draft = transform(draft)
    }

    fun selectMeal(newMeal: Meal) {
        meal = newMeal
    }

    /** [defaultLabel] is used when no label was typed. */
    fun save(defaultLabel: String) {
        val nutrients = evaluation.nutrients ?: return
        if (!canSave) return
        val label = evaluation.label.ifBlank { defaultLabel }
        saving = true
        viewModelScope.launch {
            try {
                val stored = existing
                if (stored != null) {
                    diary.update(stored.copy(meal = meal, name = label, nutrients = nutrients))
                } else {
                    diary.add(
                        DiaryEntry(
                            date = date,
                            meal = meal,
                            source = FoodSource.QUICK,
                            foodRef = null,
                            name = label,
                            brand = null,
                            amount = 1.0,
                            unit = AmountUnit.PORTION,
                            grams = 0.0,
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
}
