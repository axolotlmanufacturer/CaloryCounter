package de.privat.kalorienzaehler.domain.diary

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NutrientsTest {

    @Test
    fun valuesScaleWithTheWeight() {
        val per100 = NutrientsPer100(kcal = 250.0, protein = 8.0, carbs = 40.0, sugar = 5.0, fat = 6.0)
        val amounts = per100.forGrams(150.0)
        assertEquals(375.0, amounts.kcal, 1e-9)
        assertEquals(12.0, amounts.protein!!, 1e-9)
        assertEquals(60.0, amounts.carbs!!, 1e-9)
        assertEquals(9.0, amounts.fat!!, 1e-9)
    }

    @Test
    fun unknownValuesStayUnknownInsteadOfBecomingZero() {
        val amounts = NutrientsPer100(kcal = 100.0, protein = 5.0).forGrams(200.0)
        assertEquals(200.0, amounts.kcal, 1e-9)
        assertEquals(10.0, amounts.protein!!, 1e-9)
        assertNull(amounts.carbs)
        assertNull(amounts.fat)
        assertEquals(false, amounts.hasAllMacros)
    }

    @Test
    fun scalingAmountsKeepsMissingValuesMissing() {
        val doubled = NutrientAmounts(100.0, protein = 4.0) * 2.0
        assertEquals(200.0, doubled.kcal, 1e-9)
        assertEquals(8.0, doubled.protein!!, 1e-9)
        assertNull(doubled.fat)
    }
}

class DiaryEntryTest {

    @Test
    fun changingTheAmountScalesWeightAndNutrientsProportionally() {
        val original = entry(amount = 150.0)
        val changed = original.withAmount(300.0)
        assertEquals(300.0, changed.amount, 1e-9)
        assertEquals(300.0, changed.grams, 1e-9)
        assertEquals(original.nutrients.kcal * 2, changed.nutrients.kcal, 1e-9)
        assertEquals(original.nutrients.protein!! * 2, changed.nutrients.protein!!, 1e-9)
        assertEquals(original.id, changed.id)
        assertEquals(original.createdAtMillis, changed.createdAtMillis)
    }

    @Test
    fun portionEntriesScaleTheirGramsByTheNumberOfPortions() {
        val twoPortions = entry(amount = 2.0, unit = "Portion (30 g)", grams = 60.0)
        val threeAndAHalf = twoPortions.withAmount(3.5)
        assertEquals(3.5, threeAndAHalf.amount, 1e-9)
        assertEquals(105.0, threeAndAHalf.grams, 1e-9)
        assertEquals(twoPortions.nutrients.kcal * 1.75, threeAndAHalf.nutrients.kcal, 1e-9)
    }

    @Test
    fun aQuickEntryHasNoAmountToChange() {
        assertFailsWith<IllegalArgumentException> { quickEntry().withAmount(2.0) }
    }

    @Test
    fun theAmountMustBePositive() {
        assertFailsWith<IllegalArgumentException> { entry().withAmount(0.0) }
    }

    @Test
    fun copyingMakesANewUnsavedEntryForAnotherDay() {
        val original = entry(id = 5, date = DAY, createdAtMillis = 100)
        val copy = original.copiedTo(LocalDate.of(2026, 10, 14), createdAtMillis = 900)
        assertEquals(0, copy.id)
        assertEquals(LocalDate.of(2026, 10, 14), copy.date)
        assertEquals(900, copy.createdAtMillis)
        assertEquals(original.nutrients, copy.nutrients)
        assertEquals(original.meal, copy.meal)
        assertEquals(original.name, copy.name)
    }

    @Test
    fun valuesPer100AreDerivedFromTheSnapshot() {
        val per100 = assertNotNull(entry(amount = 150.0).per100())
        assertEquals(200.0, per100.kcal, 1e-9)
        assertEquals(10.0, per100.protein!!, 1e-9)
        assertEquals(30.0, per100.carbs!!, 1e-9)
        assertEquals(5.0, per100.fat!!, 1e-9)
        assertNull(per100.sugar)
    }

    @Test
    fun aQuickEntryHasNoValuesPer100() {
        assertNull(quickEntry().per100())
    }

    @Test
    fun theUnitOfAnEntryIsReconstructedFromItsSnapshot() {
        val unit = AmountUnit.of(entry(amount = 2.0, unit = "Portion (30 g)", grams = 60.0))
        assertEquals("Portion (30 g)", unit.key)
        assertEquals(30.0, unit.gramsPerUnit, 1e-9)
        assertEquals(90.0, unit.toGrams(3.0), 1e-9)
    }
}

class AmountUnitTest {

    @Test
    fun foodsWithoutAServingOnlyOfferGramsOrMilliliters() {
        assertEquals(listOf(AmountUnit("g", 1.0)), AmountUnit.forFood(PerUnit.GRAM, null))
        assertEquals(listOf(AmountUnit("ml", 1.0)), AmountUnit.forFood(PerUnit.MILLILITER, null))
    }

    @Test
    fun aKnownServingAddsAPortionUnit() {
        val units = AmountUnit.forFood(PerUnit.GRAM, 30.0)
        assertEquals(listOf("g", "Portion (30 g)"), units.map { it.key })
        assertEquals(30.0, units[1].gramsPerUnit, 1e-9)
        assertEquals(true, units[0].isBase)
        assertEquals(false, units[1].isBase)
    }

    @Test
    fun theLabelNamesTheServingInGramsOrMilliliters() {
        assertEquals("Portion (30 g)", AmountUnit.portion(30.0, PerUnit.GRAM).key)
        assertEquals("Portion (37,5 g)", AmountUnit.portion(37.5, PerUnit.GRAM).key)
        assertEquals("Portion (250 ml)", AmountUnit.portion(250.0, PerUnit.MILLILITER).key)
        assertEquals("Portion (330 ml)", AmountUnit.forFood(PerUnit.MILLILITER, 330.0)[1].key)
    }
}
