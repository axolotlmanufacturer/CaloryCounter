package de.privat.kalorienzaehler.data.userdb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import de.privat.kalorienzaehler.data.repository.RoomCustomFoodRepository
import de.privat.kalorienzaehler.data.repository.RoomDiaryRepository
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.diary.NutrientsPer100
import de.privat.kalorienzaehler.domain.diary.PerUnit
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomFoodRepositoryTest {

    private lateinit var database: UserDatabase
    private lateinit var foods: RoomCustomFoodRepository
    private lateinit var diary: RoomDiaryRepository

    private val today = LocalDate.of(2026, 10, 13)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        foods = RoomCustomFoodRepository(database.customFoodDao())
        diary = RoomDiaryRepository(database, database.diaryDao())
    }

    @After
    fun close() = database.close()

    private fun food(name: String, barcode: String? = null, kcal: Double = 100.0) = CustomFood(
        barcode = barcode,
        name = name,
        brand = null,
        nutrients = NutrientsPer100(kcal),
        perUnit = PerUnit.GRAM,
        servingG = null,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private suspend fun log(foodId: Long, date: LocalDate = today, createdAt: Long = 1, source: FoodSource = FoodSource.CUSTOM) {
        diary.add(
            DiaryEntry(
                date = date,
                meal = Meal.SNACKS,
                source = source,
                foodRef = foodId.toString(),
                name = "x",
                brand = null,
                amount = 100.0,
                unit = "g",
                grams = 100.0,
                nutrients = NutrientAmounts(100.0),
                createdAtMillis = createdAt,
            ),
        )
    }

    @Test
    fun aFoodComesBackExactlyAsSaved() = runTest {
        val saved = CustomFood(
            barcode = "4006381333931",
            name = "Müsli",
            brand = "Kölln",
            nutrients = NutrientsPer100(372.0, 13.5, 58.7, 1.1, 7.0, 10.0, 0.02),
            perUnit = PerUnit.MILLILITER,
            servingG = 250.0,
            createdAtMillis = 111,
            updatedAtMillis = 222,
        )
        val id = foods.save(saved)
        assertNotEquals(0, id)
        assertEquals(saved.copy(id = id), foods.get(id))
    }

    @Test
    fun unknownNutrientsStayNullAfterStorage() = runTest {
        val id = foods.save(food("Wasser", kcal = 0.0))
        val loaded = checkNotNull(foods.get(id))
        assertNull(loaded.nutrients.protein)
        assertNull(loaded.nutrients.salt)
        assertNull(loaded.servingG)
        assertNull(loaded.barcode)
    }

    @Test
    fun savingAnExistingFoodUpdatesItInPlace() = runTest {
        val id = foods.save(food("Haferflocken"))
        val returned = foods.save(checkNotNull(foods.get(id)).copy(name = "Haferflocken zart", updatedAtMillis = 9))
        assertEquals(id, returned)
        val all = foods.observeAll().first()
        assertEquals(listOf("Haferflocken zart"), all.map { it.name })
        assertEquals(9, all.single().updatedAtMillis)
    }

    @Test
    fun allFoodsAreSortedAlphabeticallyIgnoringCase() = runTest {
        foods.save(food("banane"))
        foods.save(food("Zwieback"))
        foods.save(food("Apfelmus"))
        foods.observeAll().test {
            assertEquals(listOf("Apfelmus", "banane", "Zwieback"), awaitItem().map { it.name })
        }
    }

    @Test
    fun umlautsSortWithTheirBaseLetterNotAfterZ() = runTest {
        listOf("Zwieback", "Öl", "Brot", "Äpfel getrocknet", "Käse", "Kuchen", "Oel-Sardinen").forEach { foods.save(food(it)) }
        assertEquals(
            listOf("Äpfel getrocknet", "Brot", "Käse", "Kuchen", "Öl", "Oel-Sardinen", "Zwieback"),
            foods.observeAll().first().map { it.name },
        )
    }

    @Test
    fun deletingAFoodRemovesIt() = runTest {
        val id = foods.save(food("Weg"))
        foods.delete(id)
        assertNull(foods.get(id))
        assertEquals(emptyList<CustomFood>(), foods.observeAll().first())
    }

    @Test
    fun aFoodIsFoundByItsBarcode() = runTest {
        val id = foods.save(food("Mit Code", barcode = "4006381333931"))
        foods.save(food("Ohne Code"))
        val dao = database.customFoodDao()
        assertEquals(id, dao.findByBarcode("4006381333931")?.id)
        assertNull(dao.findByBarcode("4006381333932"))
    }

    @Test
    fun recentFoodsAreOrderedByHowOftenTheyWereLogged() = runTest {
        val a = foods.save(food("A"))
        val b = foods.save(food("B"))
        val c = foods.save(food("C"))
        log(a, createdAt = 10)
        log(b, createdAt = 20)
        log(b, createdAt = 30)
        log(c, createdAt = 40)
        log(c, createdAt = 50)
        log(c, createdAt = 60)
        val recent = foods.observeRecent(since = today.minusDays(30), limit = 10).first()
        assertEquals(listOf("C", "B", "A"), recent.map { it.name })
    }

    @Test
    fun equallyOftenUsedFoodsShowTheMostRecentFirst() = runTest {
        val a = foods.save(food("A"))
        val b = foods.save(food("B"))
        log(a, createdAt = 10)
        log(b, createdAt = 20)
        assertEquals(listOf("B", "A"), foods.observeRecent(today.minusDays(30), 10).first().map { it.name })
    }

    @Test
    fun recentFoodsIgnoreOldEntriesQuickEntriesAndDeletedFoods() = runTest {
        val old = foods.save(food("Alt"))
        val fresh = foods.save(food("Frisch"))
        val gone = foods.save(food("Gelöscht"))
        log(old, date = today.minusDays(31))
        log(fresh, date = today.minusDays(30))
        log(gone)
        foods.delete(gone)
        log(fresh, source = FoodSource.QUICK)
        val recent = foods.observeRecent(since = today.minusDays(30), limit = 10).first()
        assertEquals(listOf("Frisch"), recent.map { it.name })
    }

    @Test
    fun recentFoodsAreLimited() = runTest {
        repeat(5) { index -> log(foods.save(food("F$index")), createdAt = index.toLong()) }
        assertEquals(3, foods.observeRecent(today.minusDays(30), limit = 3).first().size)
    }

    @Test
    fun recentFoodsUpdateWhenSomethingIsLogged() = runTest {
        val a = foods.save(food("A"))
        foods.observeRecent(today.minusDays(30), 10).test {
            assertEquals(emptyList<CustomFood>(), awaitItem())
            log(a)
            assertEquals(listOf("A"), awaitItem().map { it.name })
        }
    }

    @Test
    fun deleteEverythingRemovesAllFoods() = runTest {
        foods.save(food("A"))
        foods.save(food("B"))
        foods.deleteEverything()
        assertEquals(emptyList<CustomFood>(), foods.observeAll().first())
    }
}
