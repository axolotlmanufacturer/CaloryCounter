package de.privat.kalorienzaehler.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.DiaryMath
import de.privat.kalorienzaehler.domain.diary.DiaryTotals
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.MealSection
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.goal.GoalPlanner
import de.privat.kalorienzaehler.domain.nutrition.MacroTargets
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The daily targets every day is measured against. */
data class DayPlan(val targetKcal: Double, val macros: MacroTargets)

/** What the Heute screen needs besides the diary itself; `plan` is `null` while no weight is known. */
data class TodayHeader(val energyUnit: EnergyUnit, val plan: DayPlan?)

/** One day of the diary, ready to show. */
data class DayData(
    val date: LocalDate,
    /** All four meals in order, empty ones included. */
    val sections: List<MealSection>,
    val totals: DiaryTotals,
    /** How many entries each meal had on the day before (for "Von gestern kopieren"). */
    val previousDayCounts: Map<Meal, Int>,
) {
    val isEmpty: Boolean get() = totals.entryCount == 0
}

/** One-off messages for the Heute screen. */
sealed interface TodayEvent {
    data class EntryDeleted(val entry: DiaryEntry) : TodayEvent

    data class MealCopied(val ids: List<Long>) : TodayEvent

    data object Failed : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val diary: DiaryRepository,
    private val clock: Clock,
) : ViewModel() {

    /** The calendar day "Heute" stands for. */
    val today: LocalDate get() = LocalDate.now(clock)

    /** `null` until the profile has been read. */
    val header: StateFlow<TodayHeader?> = combine(profiles.profile, weights.observeLatest()) { profile, latestWeight ->
        profile?.let {
            val plan = latestWeight?.let { weight ->
                GoalPlanner.plan(profile, weight.kg, today).let { DayPlan(it.targetKcal, it.macros) }
            }
            TodayHeader(profile.energyUnit, plan)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val eventChannel = Channel<TodayEvent>(Channel.BUFFERED)
    val events: Flow<TodayEvent> = eventChannel.receiveAsFlow()

    /** One day of the diary; the day before is observed too, for "Von gestern kopieren". */
    fun day(date: LocalDate): Flow<DayData> =
        combine(diary.observeDay(date), diary.observeDay(date.minusDays(1))) { entries, previous ->
            DayData(
                date = date,
                sections = DiaryMath.sections(entries),
                totals = DiaryMath.totals(entries),
                previousDayCounts = previous.groupingBy { it.meal }.eachCount(),
            )
        }.distinctUntilChanged()

    fun delete(entry: DiaryEntry) = launchReporting {
        diary.delete(entry.id)
        eventChannel.send(TodayEvent.EntryDeleted(entry))
    }

    fun restore(entry: DiaryEntry) = launchReporting { diary.restore(entry) }

    /** Copies the same meal of the day before onto [date]. */
    fun copyFromPreviousDay(date: LocalDate, meal: Meal) = launchReporting {
        val ids = diary.copyMeal(date.minusDays(1), date, meal, clock.millis())
        if (ids.isNotEmpty()) eventChannel.send(TodayEvent.MealCopied(ids))
    }

    fun undoCopy(ids: List<Long>) = launchReporting { diary.deleteAll(ids) }

    private fun launchReporting(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventChannel.send(TodayEvent.Failed)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
