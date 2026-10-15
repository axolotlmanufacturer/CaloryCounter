package de.privat.kalorienzaehler.domain.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Python builder (`tools/food-db-builder/normalize.py`) normalizes the stored text with the same
 * rules; both are tested against `normalize_cases.json`, so a search typed in the app finds what the
 * builder indexed (PROMPT.md section 5.2).
 */
class TextNormalizerSharedCasesTest {

    private val cases: JsonArray = run {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("normalize_cases.json")) {
            "normalize_cases.json is not on the test classpath"
        }
        Json.parseToJsonElement(stream.bufferedReader(Charsets.UTF_8).readText()).jsonObject.getValue("cases").jsonArray
    }

    @Test
    fun theSharedFileHasCases() {
        assertTrue(cases.size >= 30, "expected the shared vectors, found ${cases.size}")
    }

    @Test
    fun everySharedVectorNormalizesToTheExpectedText() {
        val failures = cases.map { it as JsonObject }.mapNotNull { case ->
            val input = case.getValue("input").jsonPrimitive.content
            val expected = case.getValue("expected").jsonPrimitive.content
            val actual = TextNormalizer.normalize(input)
            if (actual == expected) null else "'$input' -> '$actual' (expected '$expected')"
        }
        assertEquals(emptyList(), failures)
    }
}
