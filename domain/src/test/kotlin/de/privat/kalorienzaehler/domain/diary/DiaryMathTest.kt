package de.privat.kalorienzaehler.domain.diary

import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryMathTest {

    @Test
    fun anEmptyDayHasZeroTotals() {
        assertEquals(DiaryTotals.EMPTY, DiaryMath.totals(emptyList()))
    }

    @Test
    fun totalsAddUpEnergyAndMacros() {
        val totals = DiaryMath.totals(
            listOf(
                entry(id = 1, amount = 100.0), // 200 kcal, 10 / 30 / 5
                entry(id = 2, amount = 50.0), // 100 kcal, 5 / 15 / 2.5
            ),
        )
        assertEquals(300.0, totals.kcal, 1e-9)
        assertEquals(15.0, totals.proteinG, 1e-9)
        assertEquals(45.0, totals.carbsG, 1e-9)
        assertEquals(7.5, totals.fatG, 1e-9)
        assertEquals(2, totals.entryCount)
        assertEquals(0, totals.entriesWithoutAllMacros)
    }

    @Test
    fun entriesWithMissingMacrosCountEnergyButAreReportedAsIncomplete() {
        val totals = DiaryMath.totals(
            listOf(
                entry(id = 1, amount = 100.0),
                quickEntry(id = 2, nutrients = NutrientAmounts(kcal = 250.0)),
                quickEntry(id = 3, nutrients = NutrientAmounts(kcal = 50.0, protein = 4.0)),
            ),
        )
        assertEquals(500.0, totals.kcal, 1e-9)
        assertEquals(14.0, totals.proteinG, 1e-9)
        assertEquals(30.0, totals.carbsG, 1e-9)
        assertEquals(5.0, totals.fatG, 1e-9)
        assertEquals(2, totals.entriesWithoutAllMacros)
    }

    @Test
    fun sectionsListAllFourMealsInOrderEvenWhenEmpty() {
        val sections = DiaryMath.sections(listOf(entry(id = 1, meal = Meal.ABENDESSEN)))
        assertEquals(
            listOf(Meal.FRUEHSTUECK, Meal.MITTAGESSEN, Meal.ABENDESSEN, Meal.SNACKS),
            sections.map { it.meal },
        )
        assertEquals(listOf(0, 0, 1, 0), sections.map { it.entries.size })
        assertEquals(DiaryTotals.EMPTY, sections.first().totals)
    }

    @Test
    fun entriesOfAMealComeOldestFirstAndTiesAreBrokenById() {
        val a = entry(id = 3, meal = Meal.SNACKS, createdAtMillis = 10)
        val b = entry(id = 1, meal = Meal.SNACKS, createdAtMillis = 30)
        val c = entry(id = 2, meal = Meal.SNACKS, createdAtMillis = 10)
        val snacks = DiaryMath.sections(listOf(b, a, c)).last()
        assertEquals(listOf(2L, 3L, 1L), snacks.entries.map { it.id })
    }

    @Test
    fun sectionTotalsArePerMeal() {
        val sections = DiaryMath.sections(
            listOf(
                entry(id = 1, meal = Meal.FRUEHSTUECK, amount = 100.0),
                entry(id = 2, meal = Meal.MITTAGESSEN, amount = 200.0),
                entry(id = 3, meal = Meal.MITTAGESSEN, amount = 100.0),
            ),
        )
        assertEquals(200.0, sections[0].totals.kcal, 1e-9)
        assertEquals(600.0, sections[1].totals.kcal, 1e-9)
    }
}

class EnergyBalanceTest {

    @Test
    fun remainingIsTargetMinusEaten() {
        val balance = EnergyBalance(targetKcal = 2209.0, eatenKcal = 975.0)
        assertEquals(1234.0, balance.remainingKcal, 1e-9)
        assertEquals(false, balance.isOver)
        assertEquals(975.0 / 2209.0, balance.fraction, 1e-9)
    }

    @Test
    fun overTheTargetIsReportedAndTheRingStopsAtFull() {
        val balance = EnergyBalance(targetKcal = 2000.0, eatenKcal = 2300.0)
        assertEquals(-300.0, balance.remainingKcal, 1e-9)
        assertEquals(true, balance.isOver)
        assertEquals(1.0, balance.fraction, 0.0)
    }

    @Test
    fun exactlyOnTargetIsNotOver() {
        assertEquals(false, EnergyBalance(2000.0, 2000.0).isOver)
    }

    @Test
    fun aZeroTargetNeverDividesByZero() {
        assertEquals(0.0, EnergyBalance(0.0, 100.0).fraction, 0.0)
    }

    @Test
    fun macroProgressClampsTheBarButKeepsTheTruth() {
        val over = MacroProgress(eatenG = 130.0, targetG = 100.0)
        assertEquals(true, over.isOver)
        assertEquals(1.0, over.fraction, 0.0)
        val under = MacroProgress(eatenG = 25.0, targetG = 100.0)
        assertEquals(false, under.isOver)
        assertEquals(0.25, under.fraction, 1e-9)
        assertEquals(0.0, MacroProgress(10.0, 0.0).fraction, 0.0)
    }
}
