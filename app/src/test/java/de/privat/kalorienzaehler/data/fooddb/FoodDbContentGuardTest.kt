package de.privat.kalorienzaehler.data.fooddb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A rebuilt `foods.db` with the same schema version must replace the copy on the device (Room alone keeps the old one). */
@RunWith(AndroidJUnit4::class)
class FoodDbContentGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val guard = AssetFoodDbContentGuard(context)
    private val installedCopy: File get() = context.getDatabasePath(FoodDatabase.NAME)
    private val marker: File get() = File(context.noBackupFilesDir, AssetFoodDbContentGuard.MARKER_NAME)

    @Before
    fun clean() {
        context.deleteDatabase(FoodDatabase.NAME)
        marker.delete()
    }

    private fun installCopy() {
        installedCopy.parentFile?.mkdirs()
        installedCopy.writeText("not really a database")
    }

    private fun packagedId() = context.assets.open(AssetFoodDbContentGuard.ID_ASSET_PATH).bufferedReader().use { it.readText().trim() }

    @Test
    fun theBuildPackagesAContentIdNextToTheDatabase() {
        assertTrue(packagedId().matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun aCopyWithoutAnyRecordOfWhereItCameFromIsReplaced() {
        installCopy()
        guard.discardStaleCopy()
        assertFalse(installedCopy.exists())
    }

    @Test
    fun aCopyFromAnotherBuildOfTheDatabaseIsReplaced() {
        installCopy()
        marker.writeText("id-of-the-sample-database-from-last-week")
        guard.discardStaleCopy()
        assertFalse(installedCopy.exists())
    }

    @Test
    fun aCopyFromThePackagedDatabaseIsKept() {
        installCopy()
        guard.markCurrent()
        guard.discardStaleCopy()
        assertTrue(installedCopy.exists())
        assertEquals(packagedId(), marker.readText())
    }

    @Test
    fun theSidecarFilesOfTheOldCopyGoToo() {
        installCopy()
        val wal = File(installedCopy.path + "-wal").apply { writeText("x") }
        guard.discardStaleCopy()
        assertFalse(installedCopy.exists())
        assertFalse(wal.exists())
    }

    @Test
    fun withoutACopyThereIsNothingToDiscard() {
        guard.discardStaleCopy()
        assertFalse(installedCopy.exists())
    }

    @Test
    fun theMarkerLivesOutsideTheBackup() {
        guard.markCurrent()
        assertTrue(marker.path.startsWith(context.noBackupFilesDir.path))
    }
}
