package de.privat.kalorienzaehler.ui.settings

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import de.privat.kalorienzaehler.domain.profile.DraftEvaluation
import de.privat.kalorienzaehler.domain.profile.ProfileDraft
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-off messages for the Profil screen. */
enum class ProfileEvent { SAVED, SAVE_FAILED }

/**
 * Editing of the saved profile. Every change recalculates the plan live (the evaluation is derived
 * from the draft); only [save] writes anything. A changed weight is stored as today's weight entry,
 * because the current weight is always the latest entry.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    private val clock: Clock,
) : ViewModel() {

    var isLoaded by mutableStateOf(false)
        private set

    var draft by mutableStateOf(ProfileDraft())
        private set

    private var baseline by mutableStateOf(ProfileDraft())
    private var storedWeightKg: Double? = null

    val evaluation: DraftEvaluation by derivedStateOf { draft.evaluate(today()) }

    val hasChanges: Boolean by derivedStateOf { isLoaded && draft != baseline }

    val canSave: Boolean by derivedStateOf { hasChanges && evaluation.isValid }

    private val eventChannel = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.profile.first()
            val latest = weightRepository.observeLatest().first()
            storedWeightKg = latest?.kg
            if (profile != null && latest != null) {
                val loaded = ProfileDraft.from(profile, latest.kg)
                draft = loaded
                baseline = loaded
            }
            isLoaded = true
        }
    }

    fun edit(transform: (ProfileDraft) -> ProfileDraft) {
        if (isLoaded) draft = transform(draft)
    }

    fun save() {
        val profile = evaluation.profile ?: return
        val weightKg = evaluation.weightKg ?: return
        if (!hasChanges) return
        val savedDraft = draft
        viewModelScope.launch {
            try {
                profileRepository.save(profile)
                if (weightKg != storedWeightKg) {
                    weightRepository.upsert(today(), weightKg)
                    storedWeightKg = weightKg
                }
                baseline = savedDraft
                eventChannel.send(ProfileEvent.SAVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventChannel.send(ProfileEvent.SAVE_FAILED)
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}
