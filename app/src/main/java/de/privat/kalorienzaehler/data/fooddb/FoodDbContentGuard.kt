package de.privat.kalorienzaehler.data.fooddb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Makes sure the food database on the device is the one that is packaged with the app.
 *
 * Room copies the asset only when no `foods.db` exists or when its `user_version` differs from
 * [FoodDatabase.VERSION]. A rebuilt database with the same schema (a newer Open Food Facts export, a filter fix, the
 * full instead of the sample database) would never replace the old copy. So the build writes a content id next to the
 * asset (`foods.db.id`, a hash of the file) and the app remembers which id its copy came from: when they differ, the
 * old copy is deleted before Room opens it and Room copies the new one. The database holds no user data.
 */
interface FoodDbContentGuard {

    /** Deletes the installed `foods.db` if it does not come from the packaged asset. Call before the first query. */
    fun discardStaleCopy()

    /** Remembers that the installed copy comes from the packaged asset. Call once the database opened. */
    fun markCurrent()
}

/** For tests that open the database themselves. */
object NoFoodDbContentGuard : FoodDbContentGuard {
    override fun discardStaleCopy() = Unit

    override fun markCurrent() = Unit
}

class AssetFoodDbContentGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) : FoodDbContentGuard {

    /** In the no-backup area: a restored backup must not claim a copy that was not restored. */
    private val marker: File get() = File(context.noBackupFilesDir, MARKER_NAME)

    private fun packagedId(): String? = try {
        context.assets.open(ID_ASSET_PATH).bufferedReader().use { it.readText().trim() }.ifEmpty { null }
    } catch (_: IOException) {
        null
    }

    private fun installedId(): String? = try {
        marker.takeIf { it.isFile }?.readText()?.trim()
    } catch (_: IOException) {
        null
    }

    override fun discardStaleCopy() {
        val packaged = packagedId() ?: return
        if (installedId() != packaged) context.deleteDatabase(FoodDatabase.NAME)
    }

    override fun markCurrent() {
        val packaged = packagedId() ?: return
        try {
            marker.writeText(packaged)
        } catch (_: IOException) {
            // Without the marker the next start just copies the database again.
        }
    }

    companion object {
        const val ID_ASSET_PATH = "databases/foods.db.id"
        const val MARKER_NAME = "foods.db.id"
    }
}
