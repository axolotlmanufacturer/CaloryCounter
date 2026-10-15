package de.privat.kalorienzaehler.domain.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FoodPlausibilityTest {

    @Test
    fun aRealisticFoodHasNoWarnings() {
        // Nutella, per 100 g: 539 kcal, 6,3 g protein, 57,5 g carbs (56,3 sugar), 30,9 g fat.
        val nutella = NutrientsPer100(539.0, protein = 6.3, carbs = 57.5, sugar = 56.3, fat = 30.9)
        assertEquals(emptyList(), FoodPlausibility.check(nutella))
    }

    @Test
    fun valuesFromARealLabelWithFibreAndSaltPass() {
        // Vollkornbrot: 205 kcal, 7 g protein, 36 g carbs, 1,3 g fat, 8 g fibre, 1,1 g salt.
        val bread = NutrientsPer100(205.0, protein = 7.0, carbs = 36.0, sugar = 3.0, fat = 1.3, fiber = 8.0, salt = 1.1)
        assertEquals(emptyList(), FoodPlausibility.check(bread))
    }

    @Test
    fun energyThatTheMacrosCannotExplainIsFlaggedWithTheExpectedValue() {
        val warnings = FoodPlausibility.check(NutrientsPer100(500.0, protein = 5.0, carbs = 20.0, fat = 2.0))
        val mismatch = assertIs<FoodWarning.KcalMacroMismatch>(warnings.single())
        assertEquals(5 * 4.0 + 20 * 4.0 + 2 * 9.0, mismatch.expectedKcal, 1e-9)
    }

    @Test
    fun smallDifferencesAreTolerated() {
        // Expected 400 kcal; 430 is within the leeway for rounding on labels.
        assertEquals(emptyList(), FoodPlausibility.check(NutrientsPer100(430.0, protein = 10.0, carbs = 60.0, fat = 13.3)))
    }

    @Test
    fun aMismatchIsOnlyJudgedWhenAllThreeMacrosAreKnown() {
        assertEquals(emptyList(), FoodPlausibility.check(NutrientsPer100(500.0, protein = 5.0, carbs = 20.0)))
        assertEquals(emptyList(), FoodPlausibility.check(NutrientsPer100(500.0)))
    }

    @Test
    fun moreThan900KcalPer100gIsFlagged() {
        val warnings = FoodPlausibility.check(NutrientsPer100(1500.0))
        assertEquals(listOf(FoodWarning.EnergyAboveMaximum), warnings)
    }

    @Test
    fun pureFatIsStillPossible() {
        assertEquals(emptyList(), FoodPlausibility.check(NutrientsPer100(900.0, protein = 0.0, carbs = 0.0, fat = 100.0)))
    }

    @Test
    fun nutrientsThatAddUpToMoreThan100gAreFlagged() {
        val warnings = FoodPlausibility.check(NutrientsPer100(400.0, protein = 40.0, carbs = 60.0, fat = 20.0))
        assertTrue(FoodWarning.NutrientsExceed100g in warnings)
    }

    @Test
    fun sugarLargerThanCarbohydratesIsFlagged() {
        val warnings = FoodPlausibility.check(NutrientsPer100(200.0, protein = 0.0, carbs = 10.0, sugar = 20.0, fat = 11.0))
        assertTrue(FoodWarning.SugarExceedsCarbs in warnings)
    }

    @Test
    fun sugarEqualToCarbohydratesIsFine() {
        val warnings = FoodPlausibility.check(NutrientsPer100(40.0, protein = 0.0, carbs = 10.0, sugar = 10.0, fat = 0.0))
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun quickEntryAmountsAreCheckedAgainstTheirMacros() {
        assertEquals(emptyList(), FoodPlausibility.checkAmounts(NutrientAmounts(250.0, 10.0, 30.0, 10.0)))
        assertIs<FoodWarning.KcalMacroMismatch>(
            FoodPlausibility.checkAmounts(NutrientAmounts(900.0, 10.0, 30.0, 10.0)).single(),
        )
        assertEquals(emptyList(), FoodPlausibility.checkAmounts(NutrientAmounts(900.0)))
    }
}
