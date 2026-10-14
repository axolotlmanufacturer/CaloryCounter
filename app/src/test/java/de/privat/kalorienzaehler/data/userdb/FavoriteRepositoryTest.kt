package de.privat.kalorienzaehler.data.userdb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import de.privat.kalorienzaehler.data.repository.RoomFavoriteRepository
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteRepositoryTest {

    private lateinit var database: UserDatabase
    private lateinit var favorites: RoomFavoriteRepository

    private val gouda = FoodKey(FoodSource.BLS, "M110100")
    private val oats = FoodKey(FoodSource.OFF, "4008400154329")
    private val own = FoodKey(FoodSource.CUSTOM, "1")

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        favorites = RoomFavoriteRepository(database.favoriteDao())
    }

    @After
    fun close() = database.close()

    @Test
    fun aStarredFoodIsFoundAgain() = runTest {
        favorites.setFavorite(gouda, true)
        favorites.setFavorite(own, true)
        assertEquals(setOf(gouda, own), favorites.observeAll().first().toSet())
        assertTrue(favorites.observeIsFavorite(gouda).first())
        assertFalse(favorites.observeIsFavorite(oats).first())
    }

    @Test
    fun starringTwiceStaysOneFavorite() = runTest {
        favorites.setFavorite(gouda, true)
        favorites.setFavorite(gouda, true)
        assertEquals(listOf(gouda), favorites.observeAll().first())
    }

    @Test
    fun removingTheStarForgetsTheFood() = runTest {
        favorites.setFavorite(gouda, true)
        favorites.setFavorite(gouda, false)
        assertEquals(emptyList<FoodKey>(), favorites.observeAll().first())
        favorites.setFavorite(gouda, false) // nothing to remove: no error
    }

    @Test
    fun theSameReferenceInAnotherSourceIsAnotherFood() = runTest {
        favorites.setFavorite(FoodKey(FoodSource.BLS, "7"), true)
        assertFalse(favorites.observeIsFavorite(FoodKey(FoodSource.CUSTOM, "7")).first())
    }

    @Test
    fun theStarFollowsChangesLive() = runTest {
        favorites.observeIsFavorite(gouda).test {
            assertFalse(awaitItem())
            favorites.setFavorite(gouda, true)
            assertTrue(awaitItem())
            favorites.setFavorite(gouda, false)
            assertFalse(awaitItem())
        }
    }

    @Test
    fun deleteEverythingRemovesAllFavorites() = runTest {
        favorites.setFavorite(gouda, true)
        favorites.setFavorite(oats, true)
        favorites.deleteEverything()
        assertEquals(emptyList<FoodKey>(), favorites.observeAll().first())
    }
}
