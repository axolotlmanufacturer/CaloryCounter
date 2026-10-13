package de.privat.kalorienzaehler.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacroCalculatorTest {

    @Test
    fun defaultIsProteinPerKgFatShareAndCarbsRemainder() {
        val t = MacroCalculator.targets(MacroSettings.Default, targetKcal = 2209.0, weightKg = 80.0)
        assertEquals(128.0, t.proteinG, 1e-9) // 1,6 g/kg
        assertEquals(2209.0 * 0.30 / 9.0, t.fatG, 1e-9) // 30 % of the calories
        assertEquals((2209.0 - 128.0 * 4 - 2209.0 * 0.30) / 4.0, t.carbsG, 1e-9)
        assertEquals(2209.0, t.kcal, 1e-9)
        assertFalse(t.proteinReduced)
    }

    @Test
    fun fatDropsToTwentyPercentWhenCarbsWouldBeNegative() {
        // 180 kg → 288 g protein = 1152 kcal; 30 % fat (450) leaves -102, 20 % fat (300) leaves 48
        val t = MacroCalculator.targets(MacroSettings.Default, targetKcal = 1500.0, weightKg = 180.0)
        assertEquals(288.0, t.proteinG, 1e-9)
        assertEquals(300.0 / 9.0, t.fatG, 1e-9)
        assertEquals(12.0, t.carbsG, 1e-9)
        assertEquals(1500.0, t.kcal, 1e-9)
        assertFalse(t.proteinReduced)
    }

    @Test
    fun proteinIsLoweredWhenEvenTwentyPercentFatLeavesNoRoom() {
        // 200 kg → 320 g protein = 1280 kcal; with 20 % fat (300) the target of 1500 is exceeded
        val t = MacroCalculator.targets(MacroSettings.Default, targetKcal = 1500.0, weightKg = 200.0)
        assertTrue(t.proteinReduced)
        assertEquals(0.0, t.carbsG, 1e-9)
        assertEquals(300.0 / 9.0, t.fatG, 1e-9)
        assertEquals(300.0, t.proteinG, 1e-9)
        assertEquals(1500.0, t.kcal, 1e-9)
    }

    @Test
    fun percentSplitUsesFourFourNine() {
        val settings = MacroSettings.PercentSplit(proteinPercent = 30.0, carbsPercent = 40.0, fatPercent = 30.0)
        assertTrue(settings.isValid)
        val t = MacroCalculator.targets(settings, targetKcal = 2000.0, weightKg = 80.0)
        assertEquals(150.0, t.proteinG, 1e-9)
        assertEquals(200.0, t.carbsG, 1e-9)
        assertEquals(2000.0 * 0.30 / 9.0, t.fatG, 1e-9)
        assertEquals(2000.0, t.kcal, 1e-9)
    }

    @Test
    fun percentSplitMustAddUpToOneHundred() {
        assertFalse(MacroSettings.PercentSplit(30.0, 40.0, 40.0).isValid)
        assertFalse(MacroSettings.PercentSplit(-10.0, 60.0, 50.0).isValid)
        assertTrue(MacroSettings.PercentSplit(33.3, 33.3, 33.4).isValid)
    }

    @Test
    fun perKilogramLeavesTheRestToCarbs() {
        val t = MacroCalculator.targets(MacroSettings.PerKilogram(2.0, 1.0), targetKcal = 2000.0, weightKg = 80.0)
        assertEquals(160.0, t.proteinG, 1e-9)
        assertEquals(80.0, t.fatG, 1e-9)
        assertEquals(160.0, t.carbsG, 1e-9) // (2000 - 640 - 720) / 4
        assertFalse(t.exceedsCalorieTarget)
    }

    @Test
    fun perKilogramFlagsValuesThatNeedMoreThanTheTarget() {
        val t = MacroCalculator.targets(MacroSettings.PerKilogram(3.0, 2.0), targetKcal = 2000.0, weightKg = 80.0)
        assertEquals(0.0, t.carbsG, 1e-9)
        assertTrue(t.exceedsCalorieTarget)
    }
}
