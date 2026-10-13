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
class DiaryRepositoryTest {

    private lateinit var database: UserDatabase
    private lateinit var diary: RoomDiaryRepository
    private lateinit var foods: RoomCustomFoodRepository

    private val today = LocalDate.of(2026, 10, 13)
    private val yesterday = today.minusDays(1)

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        diary = RoomDiaryRepository(database, database.diaryDao())
        foods = RoomCustomFoodRepository(database.customFoodDao())
    }

    @After
    fun close() = database.close()

    private fun entry(
        date: LocalDate = today,
        meal: Meal = Meal.FRUEHSTUECK,
        name: String = "Haferbrei",
        createdAt: Long = 1,
        source: FoodSource = FoodSource.CUSTOM,
        foodRef: String? = "1",
        nutrients: NutrientAmounts = NutrientAmounts(300.0, 10.0, 50.0, 5.0),
    ) = DiaryEntry(
        date = date,
        meal = meal,
        source = source,
        foodRef = foodRef,
        name = name,
        brand = null,
        amount = 150.0,
        unit = "g",
        grams = 150.0,
        nutrients = nutrients,
        createdAtMillis = createdAt,
    )

    @Test
    fun anEntryComesBackExactlyAsStored() = runTest {
        val stored = entry(nutrients = NutrientAmounts(412.5, protein = null, carbs = 30.25, fat = null))
            .copy(brand = "Kölln", amount = 2.0, unit = "Portion", grams = 60.0)
        val id = diary.add(stored)
        assertEquals(stored.copy(id = id), diary.get(id))
    }

    @Test
    fun aDayShowsOnlyItsOwnEntriesOldestFirst() = runTest {
        diary.add(entry(name = "spät", createdAt = 30))
        diary.add(entry(name = "früh", createdAt = 10))
        diary.add(entry(name = "gestern", date = yesterday))
        diary.observeDay(today).test {
            assertEquals(listOf("früh", "spät"), awaitItem().map { it.name })
        }
    }

    @Test
    fun observersAreToldAboutNewChangedAndDeletedEntries() = runTest {
        diary.observeDay(today).test {
            assertEquals(emptyList<DiaryEntry>(), awaitItem())
            val id = diary.add(entry())
            assertEquals(1, awaitItem().size)
            diary.update(checkNotNull(diary.get(id)).withAmount(300.0))
            assertEquals(300.0, awaitItem().single().amount, 0.0)
            diary.delete(id)
            assertEquals(emptyList<DiaryEntry>(), awaitItem())
        }
    }

    @Test
    fun updatingAnEntryCanMoveItToAnotherMeal() = runTest {
        val id = diary.add(entry(meal = Meal.FRUEHSTUECK))
        diary.update(checkNotNull(diary.get(id)).copy(meal = Meal.ABENDESSEN))
        assertEquals(Meal.ABENDESSEN, diary.get(id)?.meal)
    }

    @Test
    fun deletingAnUnknownIdDoesNothing() = runTest {
        diary.delete(12345)
        assertNull(diary.get(12345))
    }

    @Test
    fun aDeletedEntryComesBackWithItsIdAndItsPlace() = runTest {
        val first = diary.add(entry(name = "eins", createdAt = 10))
        val second = diary.add(entry(name = "zwei", createdAt = 20))
        val third = diary.add(entry(name = "drei", createdAt = 30))
        val removed = checkNotNull(diary.get(second))
        diary.delete(second)
        diary.restore(removed)
        val entries = diary.observeDay(today).first()
        assertEquals(listOf(first, second, third), entries.map { it.id })
        assertEquals(removed, entries[1])
    }

    @Test
    fun copyingAMealDuplicatesOnlyThatMealOntoTheOtherDay() = runTest {
        diary.add(entry(date = yesterday, meal = Meal.FRUEHSTUECK, name = "Müsli", createdAt = 10))
        diary.add(entry(date = yesterday, meal = Meal.FRUEHSTUECK, name = "Kaffee", createdAt = 20))
        diary.add(entry(date = yesterday, meal = Meal.ABENDESSEN, name = "Suppe", createdAt = 30))
        diary.add(entry(date = today, meal = Meal.MITTAGESSEN, name = "schon da", createdAt = 40))

        val ids = diary.copyMeal(yesterday, today, Meal.FRUEHSTUECK, createdAtMillis = 999)

        assertEquals(2, ids.size)
        val todays = diary.observeDay(today).first()
        // The copies are stamped 999, so they follow the entry created at 40; their own order is kept.
        assertEquals(listOf("schon da", "Müsli", "Kaffee"), todays.map { it.name })
        val copies = todays.filter { it.id in ids }
        assertEquals(setOf(Meal.FRUEHSTUECK), copies.map { it.meal }.toSet())
        assertEquals(setOf(999L), copies.map { it.createdAtMillis }.toSet())
        assertEquals(listOf("Müsli", "Kaffee"), copies.map { it.name })
        // The originals are untouched.
        assertEquals(3, diary.observeDay(yesterday).first().size)
    }

    @Test
    fun copiesKeepTheSnapshotValuesIncludingUnknownMacros() = runTest {
        val original = entry(
            date = yesterday,
            nutrients = NutrientAmounts(250.0, protein = null, carbs = 20.0, fat = null),
            source = FoodSource.QUICK,
            foodRef = null,
        )
        diary.add(original)
        val copy = diary.copyMeal(yesterday, today, Meal.FRUEHSTUECK, 5).single().let { checkNotNull(diary.get(it)) }
        assertEquals(original.nutrients, copy.nutrients)
        assertEquals(FoodSource.QUICK, copy.source)
        assertNull(copy.foodRef)
        assertEquals(today, copy.date)
    }

    @Test
    fun copyingAMealWithoutEntriesChangesNothing() = runTest {
        assertEquals(emptyList<Long>(), diary.copyMeal(yesterday, today, Meal.SNACKS, 5))
        assertEquals(emptyList<DiaryEntry>(), diary.observeDay(today).first())
    }

    @Test
    fun aCopyCanBeUndoneByItsIds() = runTest {
        diary.add(entry(date = yesterday, name = "Müsli"))
        diary.add(entry(date = today, name = "bleibt"))
        val ids = diary.copyMeal(yesterday, today, Meal.FRUEHSTUECK, 5)
        diary.deleteAll(ids)
        assertEquals(listOf("bleibt"), diary.observeDay(today).first().map { it.name })
        assertEquals(1, diary.observeDay(yesterday).first().size)
    }

    @Test
    fun copiedEntriesGetNewIds() = runTest {
        val original = diary.add(entry(date = yesterday))
        val copy = diary.copyMeal(yesterday, today, Meal.FRUEHSTUECK, 5).single()
        assertNotEquals(original, copy)
    }

    @Test
    fun deleteEverythingEmptiesTheDiary() = runTest {
        diary.add(entry())
        diary.add(entry(date = yesterday))
        diary.deleteEverything()
        assertEquals(emptyList<DiaryEntry>(), diary.observeDay(today).first())
        assertEquals(emptyList<DiaryEntry>(), diary.observeDay(yesterday).first())
    }

    @Test
    fun historyStaysIntactWhenTheCustomFoodIsEditedOrDeleted() = runTest {
        val foodId = foods.save(customFood("Haferflocken", kcal = 372.0))
        val id = diary.add(entry(foodRef = foodId.toString(), name = "Haferflocken"))
        foods.save(checkNotNull(foods.get(foodId)).copy(name = "Anders", nutrients = NutrientsPer100(10.0)))
        foods.delete(foodId)
        val kept = checkNotNull(diary.get(id))
        assertEquals("Haferflocken", kept.name)
        assertEquals(300.0, kept.nutrients.kcal, 0.0)
    }

    private fun customFood(name: String, kcal: Double = 100.0, barcode: String? = null) = CustomFood(
        barcode = barcode,
        name = name,
        brand = null,
        nutrients = NutrientsPer100(kcal),
        perUnit = PerUnit.GRAM,
        servingG = null,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
