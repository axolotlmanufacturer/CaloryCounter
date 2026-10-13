package de.privat.kalorienzaehler.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecimalInputTest {

    @Test
    fun acceptsCommaAndDotAsDecimalSeparator() {
        assertEquals(72.4, DecimalInput.parse("72,4"))
        assertEquals(72.4, DecimalInput.parse("72.4"))
        assertEquals(72.0, DecimalInput.parse("72"))
        assertEquals(0.5, DecimalInput.parse(",5"))
        assertEquals(0.5, DecimalInput.parse(".5"))
        assertEquals(72.0, DecimalInput.parse("72,"))
        assertEquals(150.0, DecimalInput.parse("  150 "))
        assertEquals(0.0, DecimalInput.parse("0"))
    }

    @Test
    fun rejectsEverythingElse() {
        listOf("", " ", ",", ".", "-1", "+1", "1,2,3", "1.234,5", "1,234.5", "abc", "1e3", "7 2", "NaN", "Infinity")
            .forEach { assertNull(DecimalInput.parse(it), "expected null for \"$it\"") }
    }

    @Test
    fun partialInputAllowsTypingStepByStep() {
        listOf("", "7", "72", "72,", "72,4", "72.4", ",", ",5").forEach {
            assertTrue(DecimalInput.isAcceptablePartial(it, maxFractionDigits = 1), "expected acceptable: \"$it\"")
        }
    }

    @Test
    fun partialInputRejectsTooManyDecimalsAndForeignCharacters() {
        listOf("72,45", "72,4,", "7a", "-7", "7 ", "1.2.3").forEach {
            assertFalse(DecimalInput.isAcceptablePartial(it, maxFractionDigits = 1), "expected rejected: \"$it\"")
        }
        assertFalse(DecimalInput.isAcceptablePartial("72,", maxFractionDigits = 0))
        assertTrue(DecimalInput.isAcceptablePartial("1500", maxFractionDigits = 0))
    }
}
