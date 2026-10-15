package de.privat.kalorienzaehler.domain.barcode

/** The retail barcode formats the scanner reads (PROMPT.md 7.5). Manual entry has no format. */
enum class BarcodeFormat { EAN_13, EAN_8, UPC_A, UPC_E }

/**
 * A valid retail barcode.
 *
 * @property code the code to show and to store (for a custom food): EAN-13, EAN-8 or UPC-A (12 digits; a UPC-E is
 *   stored expanded to its UPC-A).
 * @property lookupCodes every spelling to try in a database, the [code] first. The same product is written as
 *   EAN-8, UPC-A (12), EAN-13 or GTIN-14 with leading zeros (a UPC-A is an EAN-13 with a leading 0), and Open Food
 *   Facts has all of them.
 * @property isInStoreCode EAN-13 starting with 20 to 29 (or a UPC-A starting with 2): shops give these out for
 *   weighed goods and their own use. No product database can contain them.
 */
data class Barcode(val code: String, val lookupCodes: List<String>, val isInStoreCode: Boolean)

enum class BarcodeError {
    /** Nothing typed. */
    EMPTY,

    /** Something other than digits (spaces and hyphens are ignored). */
    NOT_DIGITS,

    /** Not 8, 12, 13 or 14 digits. */
    WRONG_LENGTH,

    /** The last digit does not fit the others: a typo (or an all-zero code, which no product has). */
    WRONG_CHECK_DIGIT,
}

sealed interface BarcodeParse {
    data class Valid(val barcode: Barcode) : BarcodeParse

    data class Invalid(val error: BarcodeError) : BarcodeParse
}

/** Check digits, UPC-E expansion and the spellings under which a code may be stored (PROMPT.md 7.5). */
object BarcodeNormalizer {

    private val LENGTHS = listOf(8, 12, 13, 14)

    /**
     * A code read by the camera. The length decides what it is (13 EAN-13, 12 UPC-A); only 8 digits are ambiguous
     * and the scanner's [format] says EAN-8 or UPC-E.
     */
    fun fromScan(text: String, format: BarcodeFormat): BarcodeParse {
        val digits = text.trim()
        return when {
            digits.isEmpty() -> BarcodeParse.Invalid(BarcodeError.EMPTY)
            !digits.all(::isAsciiDigit) -> BarcodeParse.Invalid(BarcodeError.NOT_DIGITS)
            digits.length == 8 && format == BarcodeFormat.UPC_E -> fromUpcE(digits)
            digits.length in LENGTHS -> fromGtin(digits)
            else -> BarcodeParse.Invalid(BarcodeError.WRONG_LENGTH)
        }
    }

    /**
     * A code typed by hand. Spaces and hyphens are ignored. Eight digits may be an EAN-8 or a UPC-E: an EAN-8 with a
     * correct check digit is one (and the UPC-E reading is tried as well when it is possible), else a UPC-E.
     */
    fun fromManualEntry(text: String): BarcodeParse {
        val digits = text.filterNot { it == ' ' || it == '-' }
        return when {
            digits.isEmpty() -> BarcodeParse.Invalid(BarcodeError.EMPTY)
            !digits.all(::isAsciiDigit) -> BarcodeParse.Invalid(BarcodeError.NOT_DIGITS)
            digits.length == 8 -> eightDigits(digits)
            digits.length in LENGTHS -> fromGtin(digits)
            else -> BarcodeParse.Invalid(BarcodeError.WRONG_LENGTH)
        }
    }

    /** Whether the last digit is the correct GTIN check digit of the others (8, 12, 13 or 14 digits). */
    fun hasValidCheckDigit(digits: String): Boolean {
        if (digits.length !in LENGTHS || !digits.all(::isAsciiDigit)) return false
        if (digits.all { it == '0' }) return false // no product has the code 0
        return checkDigit(digits.dropLast(1)) == digits.last() - '0'
    }

    /**
     * The 12-digit UPC-A that a UPC-E (number system, six digits, check digit) stands for, or `null` if [upcE] is no
     * valid UPC-E: not eight digits, a number system other than 0 and 1, or a check digit that does not fit.
     */
    fun expandUpcE(upcE: String): String? {
        if (upcE.length != 8 || !upcE.all(::isAsciiDigit) || upcE[0] !in "01") return null
        val system = upcE[0]
        val d1 = upcE[1]
        val d2 = upcE[2]
        val d3 = upcE[3]
        val d4 = upcE[4]
        val d5 = upcE[5]
        val d6 = upcE[6]
        val body = when (d6) {
            '0', '1', '2' -> "$system$d1$d2$d6" + "0000" + "$d3$d4$d5"
            '3' -> "$system$d1$d2$d3" + "00000" + "$d4$d5"
            '4' -> "$system$d1$d2$d3$d4" + "00000" + "$d5"
            else -> "$system$d1$d2$d3$d4$d5" + "0000" + "$d6"
        }
        val upcA = body + upcE[7]
        return upcA.takeIf { hasValidCheckDigit(it) }
    }

    private fun fromUpcE(digits: String): BarcodeParse {
        val upcA = expandUpcE(digits) ?: return BarcodeParse.Invalid(BarcodeError.WRONG_CHECK_DIGIT)
        return BarcodeParse.Valid(barcodeOf(upcA, extra = emptyList()))
    }

    private fun eightDigits(digits: String): BarcodeParse {
        val upcA = expandUpcE(digits)
        return when {
            hasValidCheckDigit(digits) -> BarcodeParse.Valid(barcodeOf(digits, extra = listOfNotNull(upcA)))
            upcA != null -> BarcodeParse.Valid(barcodeOf(upcA, extra = emptyList()))
            else -> BarcodeParse.Invalid(BarcodeError.WRONG_CHECK_DIGIT)
        }
    }

    private fun fromGtin(digits: String): BarcodeParse =
        if (hasValidCheckDigit(digits)) BarcodeParse.Valid(barcodeOf(digits, extra = emptyList())) else BarcodeParse.Invalid(BarcodeError.WRONG_CHECK_DIGIT)

    private fun barcodeOf(code: String, extra: List<String>): Barcode =
        Barcode(code, (spellings(code) + extra.flatMap(::spellings)).distinct(), isInStoreCode(code))

    /** The code itself, then the same number written with leading zeros as EAN-8, UPC-A, EAN-13 and GTIN-14. */
    private fun spellings(code: String): List<String> {
        val significant = code.trimStart('0').length
        return listOf(code) + LENGTHS.filter { it >= significant && it != code.length }.map { code.trimStart('0').padStart(it, '0') }
    }

    /** Shops use EAN-13 20-29 and UPC-A 2 for weighed goods and own labels. EAN-8 20-29 are real products (Lidl). */
    private fun isInStoreCode(code: String): Boolean {
        val upcA = when {
            code.length == 12 -> code
            code.length == 13 && code[0] == '0' -> code.substring(1) // a UPC-A written as EAN-13
            else -> null
        }
        return when {
            upcA != null -> upcA[0] == '2'
            code.length == 13 -> code.take(2).toInt() in 20..29
            else -> false
        }
    }

    private fun checkDigit(body: String): Int {
        val total = body.reversed().withIndex().sumOf { (index, char) -> (char - '0') * if (index % 2 == 0) 3 else 1 }
        return (10 - total % 10) % 10
    }

    private fun isAsciiDigit(char: Char): Boolean = char in '0'..'9'
}
