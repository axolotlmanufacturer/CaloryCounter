package de.privat.kalorienzaehler.ui

import app.cash.turbine.test
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.sampleProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun withoutAProfileTheAppAsksForOnboarding() = runTest {
        val viewModel = AppViewModel(FakeProfileRepository())
        viewModel.state.test {
            assertEquals(AppState.NEEDS_ONBOARDING, expectMostRecentItem())
        }
    }

    @Test
    fun withAProfileTheAppIsReady() = runTest {
        val repository = FakeProfileRepository().apply { preset(sampleProfile()) }
        AppViewModel(repository).state.test {
            assertEquals(AppState.READY, expectMostRecentItem())
        }
    }

    @Test
    fun savingTheProfileSwitchesFromOnboardingToTheApp() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = AppViewModel(repository)
        viewModel.state.test {
            assertEquals(AppState.NEEDS_ONBOARDING, expectMostRecentItem())
            repository.save(sampleProfile())
            assertEquals(AppState.READY, awaitItem())
            repository.clear()
            assertEquals(AppState.NEEDS_ONBOARDING, awaitItem())
        }
    }
}
