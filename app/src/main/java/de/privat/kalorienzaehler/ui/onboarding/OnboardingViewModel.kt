package de.privat.kalorienzaehler.ui.onboarding

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.data.repository.FoodRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import de.privat.kalorienzaehler.domain.profile.DraftEvaluation
import de.privat.kalorienzaehler.domain.profile.DraftField
import de.privat.kalorienzaehler.domain.profile.ProfileDraft
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The six onboarding steps (PROMPT.md §7.1), in order. */
enum class OnboardingStep {
    WELCOME,
    BASICS,
    BODY,
    ACTIVITY,
    GOAL,
    SUMMARY,
    ;

    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)

    /** Whether the answers of this step are good enough to continue. */
    fun isComplete(evaluation: DraftEvaluation): Boolean {
        val errors = evaluation.errors.keys
        return when (this) {
            WELCOME -> true
            BASICS -> DraftField.SEX !in errors && DraftField.BIRTH_DATE !in errors
            BODY -> DraftField.HEIGHT !in errors && DraftField.WEIGHT !in errors
            ACTIVITY -> DraftField.ACTIVITY !in errors
            GOAL -> DraftField.GOAL_WEIGHT !in errors
            SUMMARY -> evaluation.isValid
        }
    }
}

/**
 * State of the first-run flow. The draft lives in Compose snapshot state so that a text field always
 * sees its own latest keystroke; finishing writes the first weight entry and then the profile (the
 * profile appearing is what switches the app from onboarding to the main screens).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    foodRepository: FoodRepository,
    private val clock: Clock,
) : ViewModel() {

    /** True while the bundled food database is still being unpacked (PROMPT.md §7.1). */
    val foodDbPreparing: StateFlow<Boolean> = foodRepository.status
        .map { it == FoodDbStatus.Preparing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), foodRepository.status.value == FoodDbStatus.Preparing)

    var step by mutableStateOf(OnboardingStep.WELCOME)
        private set

    var draft by mutableStateOf(ProfileDraft())
        private set

    var isSaving by mutableStateOf(false)
        private set

    var saveFailed by mutableStateOf(false)
        private set

    val evaluation: DraftEvaluation by derivedStateOf { draft.evaluate(today()) }

    val canContinue: Boolean by derivedStateOf { step.isComplete(evaluation) }

    fun edit(transform: (ProfileDraft) -> ProfileDraft) {
        draft = transform(draft)
        saveFailed = false
    }

    fun next() {
        if (canContinue) step.next()?.let { step = it }
    }

    /** Goes one step back; returns false on the first step (nothing to go back to). */
    fun back(): Boolean {
        val previous = step.previous() ?: return false
        step = previous
        return true
    }

    fun finish() {
        val profile = evaluation.profile ?: return
        val weightKg = evaluation.weightKg ?: return
        if (isSaving) return
        isSaving = true
        saveFailed = false
        viewModelScope.launch {
            try {
                weightRepository.upsert(today(), weightKg)
                profileRepository.save(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveFailed = true
            } finally {
                isSaving = false
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}
