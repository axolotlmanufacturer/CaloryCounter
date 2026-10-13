package de.privat.kalorienzaehler.ui.today

import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.testEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val profiles = FakeProfileRepository().apply { preset(sampleProfile()) }
    private val weights = FakeWeightRepository().apply { preset(WeightPoint(TEST_TODAY, 80.0)) }
    private val diary = FakeDiaryRepository()
    private fun viewModel() = TodayViewModel(profiles, weights, diary, TEST_CLOCK)

    private val yesterday = TEST_TODAY.minusDays(1)

    @Test
    fun todayComesFromTheClock() {
        assertEquals(TEST_TODAY, viewModel().today)
    }

    @Test
    fun theHeaderCarriesTheDailyTargetAndMacroGoals() = runTest {
        viewModel().header.test {
            var header = awaitItem()
            if (header == null) header = awaitItem()
            val plan = checkNotNull(header!!.plan)
            assertEquals(2209.0, plan.targetKcal, 1e-9) // PROMPT.md §6.8, first vector
            assertEquals(128.0, plan.macros.proteinG, 1e-9) // 1,6 g/kg at 80 kg
            assertEquals(EnergyUnit.KCAL, header.energyUnit)
        }
    }

    @Test
    fun theHeaderFollowsTheEnergyUnitOfTheProfile() = runTest {
        profiles.preset(sampleProfile(energyUnit = EnergyUnit.KJ))
        assertEquals(EnergyUnit.KJ, viewModel().header.first { it != null }?.energyUnit)
    }

    @Test
    fun withoutAWeightThereIsNoTargetButTheDiaryStillWorks() = runTest {
        weights.preset()
        val header = viewModel().header.first { it != null }
        assertNull(header?.plan)
    }

    @Test
    fun thereIsNoHeaderBeforeOnboarding() = runTest {
        profiles.preset(null)
        viewModel().header.test { assertNull(awaitItem()) }
    }

    @Test
    fun aDayGroupsItsEntriesByMealAndSumsThem() = runTest {
        diary.preset(
            testEntry(meal = Meal.FRUEHSTUECK, grams = 100.0, createdAtMillis = 1),
            testEntry(meal = Meal.ABENDESSEN, grams = 50.0, createdAtMillis = 2),
            testEntry(date = yesterday, meal = Meal.MITTAGESSEN, grams = 300.0),
        )
        val day = viewModel().day(TEST_TODAY).first()
        assertEquals(TEST_TODAY, day.date)
        assertEquals(listOf(1, 0, 1, 0), day.sections.map { it.entries.size })
        assertEquals(300.0, day.totals.kcal, 1e-9)
        assertEquals(false, day.isEmpty)
    }

    @Test
    fun anEmptyDayIsEmpty() = runTest {
        assertTrue(viewModel().day(TEST_TODAY).first().isEmpty)
    }

    @Test
    fun theDayBeforeTellsWhichMealsCanBeCopied() = runTest {
        diary.preset(
            testEntry(date = yesterday, meal = Meal.FRUEHSTUECK),
            testEntry(date = yesterday, meal = Meal.FRUEHSTUECK),
            testEntry(date = yesterday, meal = Meal.SNACKS),
        )
        val day = viewModel().day(TEST_TODAY).first()
        assertEquals(mapOf(Meal.FRUEHSTUECK to 2, Meal.SNACKS to 1), day.previousDayCounts)
    }

    @Test
    fun theDayUpdatesWhenSomethingIsLogged() = runTest {
        val viewModel = viewModel()
        viewModel.day(TEST_TODAY).test {
            assertTrue(awaitItem().isEmpty)
            diary.add(testEntry())
            assertEquals(1, awaitItem().totals.entryCount)
        }
    }

    @Test
    fun deletingRemovesTheEntryAndReportsItForUndo() = runTest {
        val entry = diary.preset(testEntry(name = "Haferbrei")).single()
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.delete(entry)
            assertEquals(TodayEvent.EntryDeleted(entry), awaitItem())
        }
        assertEquals(emptyList<Any>(), diary.stored)
    }

    @Test
    fun restoringBringsTheEntryBackExactly() = runTest {
        val entry = diary.preset(testEntry(name = "Haferbrei")).single()
        val viewModel = viewModel()
        viewModel.delete(entry)
        viewModel.restore(entry)
        assertEquals(listOf(entry), diary.stored)
    }

    @Test
    fun copyingAMealAddsTheEntriesAndReportsTheirIdsForUndo() = runTest {
        diary.preset(
            testEntry(date = yesterday, meal = Meal.FRUEHSTUECK, name = "Müsli", createdAtMillis = 1),
            testEntry(date = yesterday, meal = Meal.FRUEHSTUECK, name = "Kaffee", createdAtMillis = 2),
            testEntry(date = yesterday, meal = Meal.ABENDESSEN, name = "Suppe"),
        )
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.copyFromPreviousDay(TEST_TODAY, Meal.FRUEHSTUECK)
            val copied = awaitItem() as TodayEvent.MealCopied
            assertEquals(2, copied.ids.size)
            val today = diary.stored.filter { it.date == TEST_TODAY }
            assertEquals(listOf("Müsli", "Kaffee"), today.map { it.name })
            assertEquals(setOf(Meal.FRUEHSTUECK), today.map { it.meal }.toSet())
            assertEquals(TEST_CLOCK.millis(), today.first().createdAtMillis)

            viewModel.undoCopy(copied.ids)
            assertEquals(emptyList<Any>(), diary.stored.filter { it.date == TEST_TODAY })
            assertEquals(3, diary.stored.size)
        }
    }

    @Test
    fun copyingAnEmptyMealSaysNothing() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.copyFromPreviousDay(TEST_TODAY, Meal.FRUEHSTUECK)
            expectNoEvents()
        }
    }

    @Test
    fun aFailedWriteIsReportedInsteadOfCrashing() = runTest {
        diary.preset(testEntry(date = yesterday, meal = Meal.FRUEHSTUECK))
        diary.failWrites = true
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.copyFromPreviousDay(TEST_TODAY, Meal.FRUEHSTUECK)
            assertEquals(TodayEvent.Failed, awaitItem())
        }
    }
}
