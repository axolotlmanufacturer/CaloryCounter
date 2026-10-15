package de.privat.kalorienzaehler.domain.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomFoodDraftTest {

    private fun valid() = CustomFoodDraft(
        name = "Haferflocken",
        brand = "Kölln",
        kcal = "372",
        protein = "13,5",
        carbs = "58,7",
        sugar = "1,1",
        fat = "7",
        fiber = "10",
        salt = "0.02",
        servingG = "40",
    )

    @Test
    fun aCompleteFormBecomesAFood() {
        val result = valid().evaluate()
        assertTrue(result.isValid)
        val input = assertNotNull(result.input)
        assertEquals("Haferflocken", input.name)
        assertEquals("Kölln", input.brand)
        assertEquals(NutrientsPer100(372.0, 13.5, 58.7, 1.1, 7.0, 10.0, 0.02), input.nutrients)
        assertEquals(PerUnit.GRAM, input.perUnit)
        assertEquals(40.0, input.servingG)
        assertNull(input.barcode)
        assertEquals(emptyList(), result.warnings)
    }

    @Test
    fun onlyNameAndCaloriesAreRequired() {
        val result = CustomFoodDraft(name = "Wasser", kcal = "0", perUnit = PerUnit.MILLILITER).evaluate()
        assertTrue(result.isValid)
        val input = assertNotNull(result.input)
        assertEquals(PerUnit.MILLILITER, input.perUnit)
        assertNull(input.brand)
        assertNull(input.nutrients.protein)
        assertNull(input.servingG)
    }

    @Test
    fun missingNameAndCaloriesAreReported() {
        val result = CustomFoodDraft().evaluate()
        assertFalse(result.isValid)
        assertEquals(FormError.REQUIRED, result.errors[CustomFoodField.NAME])
        assertEquals(FormError.REQUIRED, result.errors[CustomFoodField.KCAL])
        assertNull(result.input)
    }

    @Test
    fun blankNameCountsAsMissing() {
        assertEquals(FormError.REQUIRED, valid().copy(name = "   ").evaluate().errors[CustomFoodField.NAME])
    }

    @Test
    fun barcodesMustBeEightToFourteenDigits() {
        fun errorFor(code: String) = valid().copy(barcode = code).evaluate().errors[CustomFoodField.BARCODE]
        assertNull(errorFor(""))
        assertNull(errorFor("12345678"))
        assertNull(errorFor("4006381333931"))
        assertNull(errorFor(" 4006381333931 "))
        assertNull(errorFor("12345678901234"))
        assertEquals(FormError.INVALID_BARCODE, errorFor("1234567"))
        assertEquals(FormError.INVALID_BARCODE, errorFor("123456789012345"))
        assertEquals(FormError.INVALID_BARCODE, errorFor("40063813339a1"))
    }

    @Test
    fun theBarcodeIsStoredTrimmed() {
        assertEquals("4006381333931", valid().copy(barcode = " 4006381333931 ").evaluate().input?.barcode)
    }

    @Test
    fun numbersAreCheckedPerField() {
        val result = valid().copy(kcal = "x", protein = "-3", fat = "2000", servingG = "0").evaluate()
        assertEquals(FormError.UNREADABLE, result.errors[CustomFoodField.KCAL])
        assertEquals(FormError.UNREADABLE, result.errors[CustomFoodField.PROTEIN])
        assertEquals(FormError.OUT_OF_RANGE, result.errors[CustomFoodField.FAT])
        assertEquals(FormError.OUT_OF_RANGE, result.errors[CustomFoodField.SERVING])
    }

    @Test
    fun typingKilojoulesIntoTheKcalFieldGivesAWarningNotAnError() {
        val result = valid().copy(kcal = "1550", protein = "", carbs = "", sugar = "", fat = "", fiber = "", salt = "").evaluate()
        assertTrue(result.isValid)
        assertTrue(FoodWarning.EnergyAboveMaximum in result.warnings)
    }

    @Test
    fun warningsAppearEvenWhileOtherFieldsAreStillIncomplete() {
        val result = CustomFoodDraft(kcal = "800", protein = "10", carbs = "10", fat = "1").evaluate()
        assertFalse(result.isValid)
        assertTrue(result.warnings.single() is FoodWarning.KcalMacroMismatch)
    }

    @Test
    fun editingStartsFromTheStoredFoodAndRoundTrips() {
        val food = CustomFood(
            id = 4,
            barcode = "4006381333931",
            name = "Müsli",
            brand = null,
            nutrients = NutrientsPer100(1234.5, 10.0, 60.25, null, 5.0, null, 0.5),
            perUnit = PerUnit.MILLILITER,
            servingG = 250.0,
            createdAtMillis = 1,
            updatedAtMillis = 2,
        )
        val draft = CustomFoodDraft.from(food)
        // No thousands separator: "1234,5" parses back, "1.234,5" would not.
        assertEquals("1234,5", draft.kcal)
        assertEquals("60,25", draft.carbs)
        assertEquals("", draft.sugar)
        val input = assertNotNull(draft.evaluate().input)
        assertEquals(food.nutrients, input.nutrients)
        assertEquals(food.barcode, input.barcode)
        assertEquals(food.perUnit, input.perUnit)
        assertEquals(food.servingG, input.servingG)
        assertNull(input.brand)
    }
}

class AmountInputTest {

    @Test
    fun readsGermanAndEnglishDecimals() {
        assertEquals(150.0, AmountInput.parse("150").amount)
        assertEquals(1.5, AmountInput.parse("1,5").amount)
        assertEquals(2.25, AmountInput.parse("2.25").amount)
    }

    @Test
    fun emptyUnreadableAndOutOfRangeAreTold() {
        assertEquals(FormError.REQUIRED, AmountInput.parse("").error)
        assertEquals(FormError.REQUIRED, AmountInput.parse("  ").error)
        assertEquals(FormError.UNREADABLE, AmountInput.parse("abc").error)
        assertEquals(FormError.OUT_OF_RANGE, AmountInput.parse("0").error)
        assertEquals(FormError.OUT_OF_RANGE, AmountInput.parse("0,05").error)
        assertEquals(FormError.OUT_OF_RANGE, AmountInput.parse("10001").error)
        assertNull(AmountInput.parse("abc").amount)
    }
}
