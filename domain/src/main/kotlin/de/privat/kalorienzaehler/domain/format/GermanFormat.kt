package de.privat.kalorienzaehler.domain.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * German number and date formatting used everywhere in the UI.
 *
 * Only the numbers are formatted here; units ("kcal", "kg", "g") are composed in the
 * app's string resources, so all user-facing words stay in `strings.xml`.
 *
 * Examples: `1.234` (whole number with thousands separator), `72,4` (one decimal),
 * `13.10.2026` (date).
 */
object GermanFormat {

    val LOCALE: Locale = Locale.GERMANY

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", LOCALE)

    /** Rounds half-up to a whole number and adds thousands separators: `1234.5` → `"1.235"`. */
    fun wholeNumber(value: Double): String = decimal(value, fractionDigits = 0)

    /**
     * Rounds half-up to exactly [fractionDigits] decimals with a decimal comma:
     * `decimal(72.44, 1)` → `"72,4"`, `decimal(72.0, 1)` → `"72,0"`.
     */
    fun decimal(value: Double, fractionDigits: Int): String {
        require(fractionDigits >= 0) { "fractionDigits must be >= 0" }
        require(value.isFinite()) { "value must be finite" }
        var rounded = BigDecimal.valueOf(value).setScale(fractionDigits, RoundingMode.HALF_UP)
        // Avoid "-0" / "-0,0" for values that round to zero.
        if (rounded.signum() == 0) rounded = rounded.abs()
        return formatter(minFraction = fractionDigits, maxFraction = fractionDigits).format(rounded)
    }

    /**
     * Rounds half-up to at most [maxFractionDigits] decimals and drops trailing zeros:
     * `upTo(72.0, 1)` → `"72"`, `upTo(72.45, 1)` → `"72,5"`. Suited to editable amounts.
     */
    fun upTo(value: Double, maxFractionDigits: Int): String {
        require(maxFractionDigits >= 0) { "maxFractionDigits must be >= 0" }
        require(value.isFinite()) { "value must be finite" }
        var rounded = BigDecimal.valueOf(value).setScale(maxFractionDigits, RoundingMode.HALF_UP)
        if (rounded.signum() == 0) rounded = rounded.abs()
        return formatter(minFraction = 0, maxFraction = maxFractionDigits).format(rounded)
    }

    /** `LocalDate.of(2026, 10, 13)` → `"13.10.2026"`. */
    fun date(date: LocalDate): String = DATE.format(date)

    private fun formatter(minFraction: Int, maxFraction: Int): DecimalFormat =
        DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(LOCALE)).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
            roundingMode = RoundingMode.HALF_UP
        }
}
