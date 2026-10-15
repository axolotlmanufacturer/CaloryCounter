package de.privat.kalorienzaehler.domain.format

/**
 * Parsing of user-typed, non-negative decimal numbers. Both `,` and `.` are accepted as the
 * decimal separator; thousands separators are not (so "1.234,5" is rejected rather than
 * silently misread).
 */
object DecimalInput {

    private val COMPLETE = Regex("""^(\d+([.,]\d*)?|[.,]\d+)$""")

    /**
     * Returns the value of [text], or `null` if it is not a valid non-negative decimal.
     * Surrounding whitespace is ignored. Accepts `"72,4"`, `"72.4"`, `"72"`, `"72,"`, `",5"`.
     */
    fun parse(text: String): Double? {
        val trimmed = text.trim()
        if (!COMPLETE.matches(trimmed)) return null
        val normalized = trimmed.replace(',', '.').let { if (it.endsWith('.')) it.dropLast(1) else it }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * Whether [text] may be shown in an input field while the user is still typing:
     * empty, digits, at most one separator and at most [maxFractionDigits] digits after it.
     * Use it to reject keystrokes; use [parse] to read the final value.
     */
    fun isAcceptablePartial(text: String, maxFractionDigits: Int): Boolean {
        require(maxFractionDigits >= 0) { "maxFractionDigits must be >= 0" }
        if (text.isEmpty()) return true
        val separatorIndex = text.indexOfFirst { it == ',' || it == '.' }
        if (separatorIndex < 0) return text.all(::isAsciiDigit)
        if (maxFractionDigits == 0) return false
        val integerPart = text.substring(0, separatorIndex)
        val fractionPart = text.substring(separatorIndex + 1)
        return integerPart.all(::isAsciiDigit) &&
            fractionPart.all(::isAsciiDigit) &&
            fractionPart.length <= maxFractionDigits
    }

    private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'
}
