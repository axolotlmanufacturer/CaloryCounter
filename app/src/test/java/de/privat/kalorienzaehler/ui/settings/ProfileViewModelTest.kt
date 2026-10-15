package de.privat.kalorienzaehler.ui.settings

import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }
    private val weights = FakeWeightRepository().apply { preset(WeightPoint(TEST_TODAY.minusDays(3), 80.0)) }
    private fun viewModel() = ProfileViewModel(profiles, weights, TEST_CLOCK)

    @Test
    fun loadsTheSavedProfileAndTheLatestWeightIntoTheDraft() {
        val viewModel = viewModel()
        assertTrue(viewModel.isLoaded)
        assertEquals("80", viewModel.draft.weightKg)
        assertEquals("180", viewModel.draft.heightCm)
        assertEquals("13.09.1996", viewModel.draft.birthDate)
        assertEquals(GoalType.LOSE, viewModel.draft.goalType)
        assertEquals("72", viewModel.draft.goalWeightKg)
        assertFalse(viewModel.hasChanges)
        assertFalse(viewModel.canSave)
    }

    @Test
    fun theLatestEntryIsTheCurrentWeight() {
        weights.preset(WeightPoint(TEST_TODAY.minusDays(10), 85.0), WeightPoint(TEST_TODAY.minusDays(2), 82.5))
        assertEquals("82,5", viewModel().draft.weightKg)
    }

    @Test
    fun theNumbersFollowEveryEditLive() {
        val viewModel = viewModel()
        assertEquals(2209.0, viewModel.evaluation.plan!!.targetKcal, 1e-9) // 0,5 kg/week at 80 kg

        viewModel.edit { it.copy(weightKg = "90") }
        val heavier = viewModel.evaluation.plan!!.targetKcal
        assertTrue("a heavier body burns more", heavier > 2209.0)

        viewModel.edit { it.copy(weeklyRateKg = 0.25) }
        assertTrue(viewModel.evaluation.plan!!.targetKcal > heavier)
    }

    @Test
    fun editsAreOnlyStoredOnSave() {
        val viewModel = viewModel()
        viewModel.edit { it.copy(weightKg = "78,5", weeklyRateKg = 0.25) }
        assertTrue(viewModel.hasChanges)
        assertTrue(viewModel.canSave)
        assertEquals(0, profiles.saveCount)
        assertEquals(0.5, profiles.stored?.goal?.weeklyRateKg)
    }

    @Test
    fun savingStoresTheProfileAndAChangedWeightAsTodaysEntry() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.edit { it.copy(weightKg = "78,5", weeklyRateKg = 0.25) }
            viewModel.save()

            assertEquals(ProfileEvent.SAVED, awaitItem())
            assertEquals(0.25, profiles.stored?.goal?.weeklyRateKg)
            assertEquals(
                listOf(WeightPoint(TEST_TODAY.minusDays(3), 80.0), WeightPoint(TEST_TODAY, 78.5)),
                weights.stored,
            )
            assertFalse("saved, nothing left to save", viewModel.hasChanges)
        }
    }

    @Test
    fun anUnchangedWeightDoesNotCreateAnEntry() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.edit { it.copy(weeklyRateKg = 0.75) }
            viewModel.save()
            assertEquals(ProfileEvent.SAVED, awaitItem())
            assertEquals(1, weights.stored.size)
        }
    }

    @Test
    fun anInvalidDraftCannotBeSaved() {
        val viewModel = viewModel()
        viewModel.edit { it.copy(heightCm = "18") }
        assertTrue(viewModel.hasChanges)
        assertFalse(viewModel.canSave)
        viewModel.save()
        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun theUnitCanBeSwitchedToKilojoule() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.edit { it.copy(energyUnit = de.privat.kalorienzaehler.domain.energy.EnergyUnit.KJ) }
            viewModel.save()
            assertEquals(ProfileEvent.SAVED, awaitItem())
            assertEquals(de.privat.kalorienzaehler.domain.energy.EnergyUnit.KJ, profiles.stored?.energyUnit)
        }
    }

    @Test
    fun aFailedSaveKeepsTheEditsAndSaysSo() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.edit { it.copy(weeklyRateKg = 0.25) }
            profiles.failSaves = true
            viewModel.save()
            assertEquals(ProfileEvent.SAVE_FAILED, awaitItem())
            assertTrue(viewModel.hasChanges)
            assertEquals(0.5, profiles.stored?.goal?.weeklyRateKg)
        }
    }

    @Test
    fun maintenanceNeedsNoGoalWeight() {
        val viewModel = viewModel()
        viewModel.edit { it.withGoalType(GoalType.MAINTAIN).copy(goalWeightKg = "") }
        assertTrue(viewModel.canSave)
        viewModel.save()
        assertEquals(Goal.MAINTAIN.type, profiles.stored?.goal?.type)
    }

    @Test
    fun editsBeforeLoadingAreIgnored() {
        profiles.preset(null)
        weights.preset()
        val viewModel = viewModel()
        assertTrue(viewModel.isLoaded)
        assertEquals("", viewModel.draft.weightKg)
    }
}
