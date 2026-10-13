package de.privat.kalorienzaehler.domain.format

import java.time.DateTimeException
import java.time.LocalDate

/** Parsing of dates typed the German way: `13.10.1990`, `1.2.1990`. */
object GermanDate {

    private val PATTERN = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""")

    /** The date, or `null` if [text] is not `T.M.JJJJ` or names a day that does not exist (31.02.2000). */
    fun parse(text: String): LocalDate? {
        val match = PATTERN.matchEntire(text.trim()) ?: return null
        val (day, month, year) = match.destructured
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null
        }
    }
}
