package de.privat.kalorienzaehler.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which top-level experience to show: onboarding until a profile exists, then the app. */
enum class AppState { LOADING, NEEDS_ONBOARDING, READY }

@HiltViewModel
class AppViewModel @Inject constructor(
    profileRepository: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = profileRepository.profile
        .map { profile -> if (profile == null) AppState.NEEDS_ONBOARDING else AppState.READY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.LOADING)
}
