package de.privat.kalorienzaehler.domain.diary

import de.privat.kalorienzaehler.domain.text.TextNormalizer

/**
 * Turns what the user typed into a full-text query and ranks what the database found (PROMPT.md section 5.2).
 */
object FoodSearch {

    /** Words shorter than this are not searched alone: "k*" matches half the database. */
    const val MIN_WORD_LENGTH = 2
    const val MAX_WORDS = 6
    const val RESULT_LIMIT = 50

    /**
     * The FTS4 expression for [query], every word a prefix: `kase* gouda*`. `null` when nothing is worth searching
     * (empty, or no word of at least [MIN_WORD_LENGTH] letters).
     */
    fun matchExpression(query: String): String? {
        val words = TextNormalizer.tokens(query).take(MAX_WORDS)
        if (words.none { it.length >= MIN_WORD_LENGTH }) return null
        return words.joinToString(" ") { "$it*" }
    }

    /** The normalized query as one string, for the "name starts with the query" test. */
    fun normalizedPrefix(query: String): String = TextNormalizer.tokens(query).take(MAX_WORDS).joinToString(" ")

    /**
     * Orders the search result in four tiers, no food twice:
     *
     * 1. the user's own foods and the foods logged in the last 30 days that match ([personal], already in the
     *    wanted order: most used first)
     * 2. BLS foods whose name starts with the query, shorter names first
     * 3. other BLS matches, shorter names first
     * 4. Open Food Facts matches in the order given ([off]: most scanned first, then shorter names)
     */
    fun rank(
        query: String,
        personal: List<FoodHit>,
        bls: List<FoodHit>,
        off: List<FoodHit>,
        limit: Int = RESULT_LIMIT,
    ): List<FoodHit> {
        val prefix = normalizedPrefix(query)
        val firstWord = prefix.substringBefore(' ')
        val (startsWith, others) = bls.partition { TextNormalizer.normalize(it.name).startsWith(prefix) }
        // "Käse, Edamer" before "Käsekuchen" for the query "käse": the whole first word matches, then shorter names.
        val byRelevance = compareBy<FoodHit>(
            { if (TextNormalizer.tokens(it.name).firstOrNull() == firstWord) 0 else 1 },
            { it.name.length },
            { it.name },
        )
        val seen = HashSet<FoodKey>()
        return (personal + startsWith.sortedWith(byRelevance) + others.sortedWith(byRelevance) + off)
            .filter { seen.add(it.key) }
            .take(limit)
    }
}
