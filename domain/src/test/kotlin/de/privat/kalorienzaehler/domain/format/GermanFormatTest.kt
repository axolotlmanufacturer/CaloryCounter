package de.privat.kalorienzaehler.domain.format

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GermanFormatTest {

    @Test
    fun wholeNumberUsesDotAsThousandsSeparator() {
        assertEquals("1.234", GermanFormat.wholeNumber(1234.0))
        assertEquals("12.345.678", GermanFormat.wholeNumber(12_345_678.0))
        assertEquals("999", GermanFormat.wholeNumber(999.0))
        assertEquals("0", GermanFormat.wholeNumber(0.0))
    }

    @Test
    fun wholeNumberRoundsHalfUp() {
        assertEquals("2.759", GermanFormat.wholeNumber(2759.0))
        assertEquals("1.884", GermanFormat.wholeNumber(1884.09375))
        assertEquals("1.235", GermanFormat.wholeNumber(1234.5))
        assertEquals("1.234", GermanFormat.wholeNumber(1234.49))
    }

    @Test
    fun negativeValuesKeepTheirSignButNeverShowMinusZero() {
        assertEquals("-120", GermanFormat.wholeNumber(-120.0))
        assertEquals("0", GermanFormat.wholeNumber(-0.4))
        assertEquals("0,0", GermanFormat.decimal(-0.04, 1))
    }

    @Test
    fun decimalUsesCommaAndFixedFractionDigits() {
        assertEquals("72,4", GermanFormat.decimal(72.4, 1))
        assertEquals("72,0", GermanFormat.decimal(72.0, 1))
        assertEquals("0,62", GermanFormat.decimal(0.6219, 2))
        assertEquals("1.370,3", GermanFormat.decimal(1370.25, 1))
    }

    @Test
    fun decimalRoundsHalfUpOnTheDecimalRepresentation() {
        // 72.45 is not exactly representable as a binary double; rounding must still give 72,5.
        assertEquals("72,5", GermanFormat.decimal(72.45, 1))
        assertEquals("0,1", GermanFormat.decimal(0.05, 1))
    }

    @Test
    fun upToDropsTrailingZeros() {
        assertEquals("72", GermanFormat.upTo(72.0, 1))
        assertEquals("72,5", GermanFormat.upTo(72.45, 1))
        assertEquals("150", GermanFormat.upTo(150.0, 2))
        assertEquals("1.234,57", GermanFormat.upTo(1234.567, 2))
    }

    @Test
    fun dateIsDayMonthYearWithDots() {
        assertEquals("13.10.2026", GermanFormat.date(LocalDate.of(2026, 10, 13)))
        assertEquals("01.03.2027", GermanFormat.date(LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { GermanFormat.decimal(1.0, -1) }
        assertFailsWith<IllegalArgumentException> { GermanFormat.wholeNumber(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { GermanFormat.upTo(Double.POSITIVE_INFINITY, 1) }
    }
}
