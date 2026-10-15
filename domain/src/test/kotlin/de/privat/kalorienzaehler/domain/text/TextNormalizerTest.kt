package de.privat.kalorienzaehler.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextNormalizerTest {

    @Test
    fun umlautsAndTheirTranscriptionsMeetInTheSameForm() {
        assertEquals("kase", TextNormalizer.normalize("Käse"))
        assertEquals("kase", TextNormalizer.normalize("Kaese"))
        assertEquals("kase", TextNormalizer.normalize("kase"))
        assertEquals("musli", TextNormalizer.normalize("Müsli"))
        assertEquals("musli", TextNormalizer.normalize("Muesli"))
        assertEquals("ol", TextNormalizer.normalize("Öl"))
        assertEquals("ol", TextNormalizer.normalize("Oel"))
    }

    @Test
    fun sharpSBecomesDoubleS() {
        assertEquals("weissbrot", TextNormalizer.normalize("Weißbrot"))
        assertEquals("strasse", TextNormalizer.normalize("STRAẞE"))
    }

    @Test
    fun otherDiacriticsAreStripped() {
        assertEquals("creme fraiche", TextNormalizer.normalize("Crème fraîche"))
        assertEquals("jalapeno", TextNormalizer.normalize("Jalapeño"))
    }

    @Test
    fun punctuationBecomesSpacesAndSpacesCollapse() {
        assertEquals("apfel roh", TextNormalizer.normalize("Apfel, roh"))
        assertEquals("brot 500 g", TextNormalizer.normalize("  Brot -- 500 g!  "))
        assertEquals("a b", TextNormalizer.normalize("a\t\n b"))
    }

    @Test
    fun anythingOutsideAsciiLettersAndDigitsIsASeparator() {
        assertEquals("a b", TextNormalizer.normalize("a€b"))
        assertEquals("", TextNormalizer.normalize("   "))
        assertEquals("", TextNormalizer.normalize("!!!"))
    }

    @Test
    fun normalizingTwiceChangesNothing() {
        listOf("Käse, Gouda 48 %", "Müsli-Riegel", "Weißbrot", "Crème fraîche", "Aepfel").forEach {
            val once = TextNormalizer.normalize(it)
            assertEquals(once, TextNormalizer.normalize(once))
        }
    }

    @Test
    fun tokensAreTheNormalizedWords() {
        assertEquals(listOf("kase", "gouda"), TextNormalizer.tokens("Käse, Gouda"))
        assertEquals(emptyList(), TextNormalizer.tokens(""))
    }

    @Test
    fun everyQueryWordMustStartSomeWordOfTheText() {
        assertTrue(TextNormalizer.matchesPrefixes("Käse Gouda jung", "kae gou"))
        assertTrue(TextNormalizer.matchesPrefixes("Käse Gouda jung", "GOUDA"))
        assertTrue(TextNormalizer.matchesPrefixes("Haferflocken Kölln", "koel haf"))
        assertFalse(TextNormalizer.matchesPrefixes("Käse Gouda", "gouda edamer"))
        assertFalse(TextNormalizer.matchesPrefixes("Käse Gouda", "ouda"))
    }

    @Test
    fun anEmptyQueryMatchesEverything() {
        assertTrue(TextNormalizer.matchesPrefixes("Käse", ""))
        assertTrue(TextNormalizer.matchesPrefixes("Käse", "  ,"))
    }
}
