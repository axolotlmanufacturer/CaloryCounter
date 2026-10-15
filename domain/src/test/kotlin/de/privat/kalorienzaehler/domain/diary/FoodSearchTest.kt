package de.privat.kalorienzaehler.domain.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FoodSearchTest {

    private fun hit(source: FoodSource, ref: String, name: String, brand: String? = null) =
        FoodHit(FoodKey(source, ref), name, brand, 100.0, PerUnit.GRAM)

    private fun bls(code: String, name: String) = hit(FoodSource.BLS, code, name)

    private fun off(code: String, name: String) = hit(FoodSource.OFF, code, name, "Marke")

    private fun custom(id: Long, name: String) = hit(FoodSource.CUSTOM, id.toString(), name)

    @Test
    fun everyWordBecomesAPrefix() {
        assertEquals("kase*", FoodSearch.matchExpression("Käse"))
        assertEquals("kase* gouda*", FoodSearch.matchExpression("  Käse, Gouda "))
        assertEquals("kase*", FoodSearch.matchExpression("Kaese"))
    }

    @Test
    fun nothingWorthSearchingGivesNoExpression() {
        assertNull(FoodSearch.matchExpression(""))
        assertNull(FoodSearch.matchExpression("   "))
        assertNull(FoodSearch.matchExpression("k"))
        assertNull(FoodSearch.matchExpression("!!"))
        assertNull(FoodSearch.matchExpression("a b"))
    }

    @Test
    fun oneLongEnoughWordIsEnoughEvenWithShortOnes() {
        assertEquals("a* kase*", FoodSearch.matchExpression("a kase"))
    }

    @Test
    fun theExpressionIsLimitedToSixWords() {
        assertEquals("aa* bb* cc* dd* ee* ff*", FoodSearch.matchExpression("aa bb cc dd ee ff gg hh"))
    }

    @Test
    fun theNormalizedQueryIsComparedWithNormalizedNames() {
        assertEquals("kase gouda", FoodSearch.normalizedPrefix("Käse,  Gouda"))
    }

    @Test
    fun tiersComeInTheOrderOfTheSpec() {
        val personal = listOf(custom(1, "Mein Käsebrot"), bls("M1", "Käse eigen").copy(key = FoodKey(FoodSource.BLS, "RECENT")))
        val result = FoodSearch.rank(
            query = "käse",
            personal = personal,
            bls = listOf(bls("M3", "Gouda Käse"), bls("M2", "Käse, Edamer"), bls("M4", "Käsekuchen")),
            off = listOf(off("4006381333931", "Käse Scheiben"), off("4008400154329", "Käsespätzle")),
        )
        assertEquals(
            listOf("Mein Käsebrot", "Käse eigen", "Käse, Edamer", "Käsekuchen", "Gouda Käse", "Käse Scheiben", "Käsespätzle"),
            result.map { it.name },
        )
    }

    @Test
    fun blsFoodsStartingWithTheQueryComeBeforeOtherBlsFoods() {
        val result = FoodSearch.rank(
            "apfel",
            emptyList(),
            bls = listOf(bls("A", "Apfelmus ungesüßt"), bls("B", "Bratapfel"), bls("C", "Apfel roh"), bls("D", "Granatapfel roh")),
            off = emptyList(),
        )
        assertEquals(listOf("Apfel roh", "Apfelmus ungesüßt", "Bratapfel", "Granatapfel roh"), result.map { it.name })
    }

    @Test
    fun startsWithIgnoresUmlautSpellings() {
        val result = FoodSearch.rank(
            "kaese",
            emptyList(),
            bls = listOf(bls("A", "Brot mit Käse"), bls("B", "Käsesuppe")),
            off = emptyList(),
        )
        assertEquals(listOf("Käsesuppe", "Brot mit Käse"), result.map { it.name })
    }

    @Test
    fun offKeepsTheOrderItWasGivenInBecauseThatIsPopularity() {
        val result = FoodSearch.rank(
            "cola",
            emptyList(),
            bls = emptyList(),
            off = listOf(off("3", "Cola Zero"), off("1", "Cola"), off("2", "Cola Light")),
        )
        assertEquals(listOf("Cola Zero", "Cola", "Cola Light"), result.map { it.name })
    }

    @Test
    fun aFoodAppearsOnlyOnceAtItsBestTier() {
        val recent = bls("M2", "Käse, Edamer")
        val result = FoodSearch.rank(
            "käse",
            personal = listOf(recent),
            bls = listOf(bls("M2", "Käse, Edamer"), bls("M5", "Käse, Gouda")),
            off = emptyList(),
        )
        assertEquals(listOf("Käse, Edamer", "Käse, Gouda"), result.map { it.name })
    }

    @Test
    fun theResultIsLimited() {
        val many = (1..80).map { off("$it", "Cola $it") }
        assertEquals(50, FoodSearch.rank("cola", emptyList(), emptyList(), many).size)
        assertEquals(3, FoodSearch.rank("cola", emptyList(), emptyList(), many, limit = 3).size)
    }

    @Test
    fun aQuickEntryIsNotAFood() {
        assertFailsWith<IllegalArgumentException> { FoodKey(FoodSource.QUICK, "x") }
    }
}

class LoggableFoodTest {

    @Test
    fun unitsListGramsThePacksPortionAndThenTheTypicalPortions() {
        val food = LoggableFood(
            key = FoodKey(FoodSource.BLS, "F110100"),
            name = "Apfel roh",
            brand = null,
            nutrients = NutrientsPer100(58.0),
            perUnit = PerUnit.GRAM,
            servingG = 30.0,
            portions = listOf(FoodPortion("Stück (mittel)", 150.0, estimated = true)),
        )
        val units = food.units()
        assertEquals(listOf("g", "Portion (30 g)", "Stück (mittel)"), units.map { it.key })
        assertEquals(listOf(false, false, true), units.map { it.estimated })
        assertEquals(150.0, units[2].gramsPerUnit)
    }

    @Test
    fun aFoodWithoutPortionsOnlyHasGrams() {
        val food = LoggableFood(FoodKey(FoodSource.OFF, "1"), "x", null, NutrientsPer100(1.0), PerUnit.MILLILITER, null, emptyList())
        assertEquals(listOf("ml"), food.units().map { it.key })
    }

    @Test
    fun customFoodsMapToTheSameShapes() {
        val custom = CustomFood(7, null, "Müsli", "Marke", NutrientsPer100(372.0), PerUnit.GRAM, 40.0, 1, 1)
        assertEquals(FoodKey(FoodSource.CUSTOM, "7"), custom.toHit().key)
        assertEquals(372.0, custom.toHit().kcalPer100)
        assertEquals(listOf("g", "Portion (40 g)"), custom.toLoggable().units().map { it.key })
    }
}
