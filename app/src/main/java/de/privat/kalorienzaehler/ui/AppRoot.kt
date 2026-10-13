package de.privat.kalorienzaehler.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.privat.kalorienzaehler.ui.onboarding.OnboardingScreen

/** First run: onboarding. Afterwards: the tabs. */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state) {
        AppState.LOADING -> LoadingScreen()
        AppState.NEEDS_ONBOARDING -> OnboardingScreen()
        AppState.READY -> KalorienzaehlerRoot()
    }
}
