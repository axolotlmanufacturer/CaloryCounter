package de.privat.kalorienzaehler.domain.text

import java.text.Normalizer
import java.util.Locale

/**
 * The search normalization of PROMPT.md §5.2. The Python food-database builder applies the identical
 * function to the stored text, so "Käse", "Kaese" and "kase" all become `kase`.
 *
 * Steps: lowercase → `ß`→`ss`, `ä`/`ae`→`a`, `ö`/`oe`→`o`, `ü`/`ue`→`u` → strip remaining diacritics →
 * everything except `a-z` and `0-9` becomes a space → collapse and trim spaces.
 */
object TextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val folded = text.lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .replace("ä", "a").replace("ö", "o").replace("ü", "u")
            .replace("ae", "a").replace("oe", "o").replace("ue", "u")
        val withoutDiacritics = COMBINING_MARKS.replace(Normalizer.normalize(folded, Normalizer.Form.NFD), "")
        return NOT_ALPHANUMERIC.replace(withoutDiacritics, " ").trim()
    }

    /** The normalized words of [text]. */
    fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Whether every word of [query] is the beginning of some word of [text] (the same idea as the
     * prefix search over the food database). An empty query matches everything.
     */
    fun matchesPrefixes(text: String, query: String): Boolean {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return true
        val textTokens = tokens(text)
        return queryTokens.all { wanted -> textTokens.any { it.startsWith(wanted) } }
    }
}
