package de.privat.kalorienzaehler.domain.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickEntryDraftTest {

    @Test
    fun anEmptyFormIsNotValidAndAsksForTheCalories() {
        val result = QuickEntryDraft().evaluate()
        assertFalse(result.isValid)
        assertEquals(FormError.REQUIRED, result.errors[QuickEntryField.KCAL])
        assertNull(result.nutrients)
    }

    @Test
    fun caloriesAloneAreEnoughAndMacrosStayUnknown() {
        val result = QuickEntryDraft(kcal = "350").evaluate()
        assertTrue(result.isValid)
        val nutrients = assertNotNull(result.nutrients)
        assertEquals(350.0, nutrients.kcal, 0.0)
        assertNull(nutrients.protein)
        assertNull(nutrients.carbs)
        assertNull(nutrients.fat)
        assertEquals("", result.label)
    }

    @Test
    fun germanDecimalCommaAndDotAreBothAccepted() {
        val comma = QuickEntryDraft(kcal = "412,5", protein = "20,5", carbs = "30.25", fat = "10").evaluate()
        val nutrients = assertNotNull(comma.nutrients)
        assertEquals(412.5, nutrients.kcal, 0.0)
        assertEquals(20.5, nutrients.protein!!, 0.0)
        assertEquals(30.25, nutrients.carbs!!, 0.0)
        assertEquals(10.0, nutrients.fat!!, 0.0)
    }

    @Test
    fun theLabelIsTrimmed() {
        assertEquals("Kaffee mit Milch", QuickEntryDraft(label = "  Kaffee mit Milch ", kcal = "30").evaluate().label)
    }

    @Test
    fun zeroCaloriesAreAllowedForWaterAndBlackCoffee() {
        assertTrue(QuickEntryDraft(kcal = "0").evaluate().isValid)
    }

    @Test
    fun unreadableAndOutOfRangeNumbersAreReportedPerField() {
        val result = QuickEntryDraft(kcal = "abc", protein = "-1", carbs = "5000", fat = "1,2,3").evaluate()
        assertEquals(FormError.UNREADABLE, result.errors[QuickEntryField.KCAL])
        assertEquals(FormError.UNREADABLE, result.errors[QuickEntryField.PROTEIN])
        assertEquals(FormError.OUT_OF_RANGE, result.errors[QuickEntryField.CARBS])
        assertEquals(FormError.UNREADABLE, result.errors[QuickEntryField.FAT])
        assertNull(result.nutrients)
    }

    @Test
    fun implausiblyHighCaloriesAreRejectedAsATypo() {
        assertEquals(FormError.OUT_OF_RANGE, QuickEntryDraft(kcal = "10000").evaluate().errors[QuickEntryField.KCAL])
    }

    @Test
    fun aTooLongLabelIsRejected() {
        val result = QuickEntryDraft(label = "x".repeat(101), kcal = "10").evaluate()
        assertEquals(FormError.TOO_LONG, result.errors[QuickEntryField.LABEL])
    }

    @Test
    fun macrosThatDoNotMatchTheCaloriesGiveAWarningButStillSave() {
        val result = QuickEntryDraft(kcal = "900", protein = "10", carbs = "30", fat = "10").evaluate()
        assertTrue(result.isValid)
        assertTrue(result.warnings.single() is FoodWarning.KcalMacroMismatch)
    }

    @Test
    fun editingAQuickEntryStartsFromItsStoredValues() {
        val stored = quickEntry(nutrients = NutrientAmounts(412.5, protein = 20.0, carbs = null, fat = 10.25))
            .copy(name = "Mittagsimbiss")
        val draft = QuickEntryDraft.from(stored)
        assertEquals("Mittagsimbiss", draft.label)
        assertEquals("412,5", draft.kcal)
        assertEquals("20", draft.protein)
        assertEquals("", draft.carbs)
        assertEquals("10,25", draft.fat)
        assertEquals(stored.nutrients, draft.evaluate().nutrients)
    }
}
