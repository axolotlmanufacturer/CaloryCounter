package de.privat.kalorienzaehler.data.prefs

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import de.privat.kalorienzaehler.domain.model.Goal
import de.privat.kalorienzaehler.testing.sampleProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreProfileRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository(file: File = File(folder.root, "settings.preferences_pb")): DataStoreProfileRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = backgroundScope,
            produceFile = { file },
        )
        return DataStoreProfileRepository(dataStore)
    }

    @Test
    fun startsWithoutAProfile() = runTest(UnconfinedTestDispatcher()) {
        repository().profile.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun savedProfileIsEmittedAndReplacedOnTheNextSave() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        repository.profile.test {
            assertNull(awaitItem())
            val first = sampleProfile()
            repository.save(first)
            assertEquals(first, awaitItem())
            val second = sampleProfile(goal = Goal.MAINTAIN)
            repository.save(second)
            assertEquals(second, awaitItem())
        }
    }

    @Test
    fun savingTheSameProfileAgainDoesNotEmitAgain() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        repository.save(sampleProfile())
        repository.profile.test {
            assertEquals(sampleProfile(), awaitItem())
            repository.save(sampleProfile())
            expectNoEvents()
        }
    }

    @Test
    fun clearGoesBackToNoProfile() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        repository.save(sampleProfile())
        repository.profile.test {
            assertEquals(sampleProfile(), awaitItem())
            repository.clear()
            assertNull(awaitItem())
        }
    }

    @Test
    fun theProfileSurvivesRestartingTheStore() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "persisted.preferences_pb")
        // The first store lives in its own scope; cancelling it stands for the app process ending.
        val firstProcess = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        val first = PreferenceDataStoreFactory.create(scope = firstProcess, produceFile = { file })
        DataStoreProfileRepository(first).save(sampleProfile())
        firstProcess.cancel()

        val reopened = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        DataStoreProfileRepository(reopened).profile.test {
            assertEquals(sampleProfile(), awaitItem())
        }
    }

    @Test
    fun aCorruptSettingsFileMeansNoProfile() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "broken.preferences_pb").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 99, 98)) }
        repository(file).profile.test {
            assertNull(awaitItem())
        }
    }
}
