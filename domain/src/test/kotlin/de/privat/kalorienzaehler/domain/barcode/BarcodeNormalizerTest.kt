package de.privat.kalorienzaehler.domain.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class BarcodeNormalizerTest {

    private fun scan(text: String, format: BarcodeFormat): Barcode = when (val parse = BarcodeNormalizer.fromScan(text, format)) {
        is BarcodeParse.Valid -> parse.barcode
        is BarcodeParse.Invalid -> fail("$text should be valid but is ${parse.error}")
    }

    private fun manual(text: String): Barcode = when (val parse = BarcodeNormalizer.fromManualEntry(text)) {
        is BarcodeParse.Valid -> parse.barcode
        is BarcodeParse.Invalid -> fail("$text should be valid but is ${parse.error}")
    }

    private fun manualError(text: String): BarcodeError = when (val parse = BarcodeNormalizer.fromManualEntry(text)) {
        is BarcodeParse.Valid -> fail("$text should be invalid")
        is BarcodeParse.Invalid -> parse.error
    }

    // --- check digits ------------------------------------------------------------------------------

    @Test
    fun checkDigitsOfRealCodes() {
        listOf("4006381333931", "5449000000996", "4008400154329", "40084107", "20003166", "036000291452", "012345678905", "04006381333931")
            .forEach { assertTrue(BarcodeNormalizer.hasValidCheckDigit(it), it) }
    }

    @Test
    fun aWrongOrMissingCheckDigitIsRejected() {
        listOf("4006381333932", "4006381333930", "40084108", "036000291453", "5449000000997")
            .forEach { assertFalse(BarcodeNormalizer.hasValidCheckDigit(it), it) }
    }

    @Test
    fun everySingleDigitTypoIsCaught() {
        val code = "4006381333931"
        for (position in code.indices) {
            for (replacement in '0'..'9') {
                if (replacement == code[position]) continue
                val typo = code.replaceRange(position, position + 1, replacement.toString())
                assertFalse(BarcodeNormalizer.hasValidCheckDigit(typo), typo)
            }
        }
    }

    @Test
    fun onlyEightTwelveThirteenAndFourteenDigitsCanBeGtins() {
        assertFalse(BarcodeNormalizer.hasValidCheckDigit(""))
        assertFalse(BarcodeNormalizer.hasValidCheckDigit("123456"))
        assertFalse(BarcodeNormalizer.hasValidCheckDigit("12345678901"))
        assertFalse(BarcodeNormalizer.hasValidCheckDigit("40063813339311"))
        assertFalse(BarcodeNormalizer.hasValidCheckDigit("400638133393a"))
    }

    @Test
    fun anAllZeroCodeIsNoProduct() {
        listOf("00000000", "000000000000", "0000000000000", "00000000000000").forEach { assertFalse(BarcodeNormalizer.hasValidCheckDigit(it), it) }
        assertEquals(BarcodeError.WRONG_CHECK_DIGIT, manualError("0000000000000"))
    }

    // --- UPC-E -------------------------------------------------------------------------------------

    @Test
    fun upcEExpandsToUpcAForEveryPattern() {
        val pairs = mapOf(
            "04252614" to "042100005264", // 6th digit 1 (the example on Wikipedia)
            "01234505" to "012000003455", // 0
            "01234523" to "012200003453", // 2
            "01234531" to "012300000451", // 3
            "01234543" to "012340000053", // 4
            "01234565" to "012345000065", // 5 to 9
            "01234596" to "012345000096",
            "11234562" to "112345000062", // number system 1
        )
        pairs.forEach { (upcE, upcA) ->
            assertEquals(upcA, BarcodeNormalizer.expandUpcE(upcE), upcE)
            assertTrue(BarcodeNormalizer.hasValidCheckDigit(upcA), upcA)
        }
    }

    @Test
    fun aUpcEWithAWrongCheckDigitOrNumberSystemDoesNotExpand() {
        assertNull(BarcodeNormalizer.expandUpcE("04252615"))
        assertNull(BarcodeNormalizer.expandUpcE("21234565")) // only number systems 0 and 1
        assertNull(BarcodeNormalizer.expandUpcE("0123456"))
        assertNull(BarcodeNormalizer.expandUpcE("012345655"))
        assertNull(BarcodeNormalizer.expandUpcE("0123456a"))
    }

    @Test
    fun aScannedUpcEIsStoredAsItsUpcAAndLookedUpInAllSpellings() {
        val barcode = scan("04252614", BarcodeFormat.UPC_E)
        assertEquals("042100005264", barcode.code)
        assertEquals(listOf("042100005264", "0042100005264", "00042100005264"), barcode.lookupCodes)
        assertFalse(barcode.isInStoreCode)
    }

    @Test
    fun aScannedUpcEWithABadCheckDigitIsInvalid() {
        assertEquals(BarcodeParse.Invalid(BarcodeError.WRONG_CHECK_DIGIT), BarcodeNormalizer.fromScan("04252615", BarcodeFormat.UPC_E))
    }

    // --- scanned codes -----------------------------------------------------------------------------

    @Test
    fun anEan13IsLookedUpAsItIsAndWithAGtin14Zero() {
        val barcode = scan("4006381333931", BarcodeFormat.EAN_13)
        assertEquals("4006381333931", barcode.code)
        assertEquals(listOf("4006381333931", "04006381333931"), barcode.lookupCodes)
        assertFalse(barcode.isInStoreCode)
    }

    @Test
    fun aUpcAIsLookedUpAs12And13And14Digits() {
        val barcode = scan("036000291452", BarcodeFormat.UPC_A)
        assertEquals("036000291452", barcode.code)
        assertEquals(listOf("036000291452", "0036000291452", "00036000291452"), barcode.lookupCodes)
    }

    @Test
    fun anEan13WithALeadingZeroIsAlsoLookedUpAsUpcA() {
        val barcode = scan("0036000291452", BarcodeFormat.EAN_13)
        assertEquals("0036000291452", barcode.code)
        assertEquals(listOf("0036000291452", "036000291452", "00036000291452"), barcode.lookupCodes)
    }

    @Test
    fun anEan8IsLookedUpInEveryLongerSpelling() {
        val barcode = scan("40084107", BarcodeFormat.EAN_8)
        assertEquals("40084107", barcode.code)
        assertEquals(listOf("40084107", "000040084107", "0000040084107", "00000040084107"), barcode.lookupCodes)
    }

    @Test
    fun theScannersFormatOnlyMattersForEightDigits() {
        // The camera library reports a UPC-A with a leading 0 as EAN-13 sometimes and vice versa: the length decides.
        assertEquals("036000291452", scan("036000291452", BarcodeFormat.EAN_13).code)
        assertEquals("4006381333931", scan("4006381333931", BarcodeFormat.UPC_A).code)
        assertEquals("40084107", scan("40084107", BarcodeFormat.EAN_8).code)
    }

    @Test
    fun aScannedCodeWithTheWrongShapeIsInvalid() {
        assertEquals(BarcodeParse.Invalid(BarcodeError.EMPTY), BarcodeNormalizer.fromScan(" ", BarcodeFormat.EAN_13))
        assertEquals(BarcodeParse.Invalid(BarcodeError.NOT_DIGITS), BarcodeNormalizer.fromScan("40063813339x1", BarcodeFormat.EAN_13))
        assertEquals(BarcodeParse.Invalid(BarcodeError.WRONG_LENGTH), BarcodeNormalizer.fromScan("400638133", BarcodeFormat.EAN_13))
        assertEquals(BarcodeParse.Invalid(BarcodeError.WRONG_CHECK_DIGIT), BarcodeNormalizer.fromScan("4006381333932", BarcodeFormat.EAN_13))
    }

    // --- in-store codes ----------------------------------------------------------------------------

    @Test
    fun ean13StartingWith20To29IsAnInStoreCode() {
        assertTrue(scan("2012345678903", BarcodeFormat.EAN_13).isInStoreCode)
        assertFalse(scan("4006381333931", BarcodeFormat.EAN_13).isInStoreCode)
    }

    @Test
    fun theRangeIs20To29AndNothingElse() {
        fun withPrefix(prefix: String): String {
            val body = prefix + "0".repeat(10)
            val total = body.reversed().withIndex().sumOf { (index, char) -> (char - '0') * if (index % 2 == 0) 3 else 1 }
            return body + ((10 - total % 10) % 10)
        }
        for (prefix in 10..39) {
            val barcode = scan(withPrefix(prefix.toString()), BarcodeFormat.EAN_13)
            assertEquals(prefix in 20..29, barcode.isInStoreCode, "prefix $prefix")
        }
    }

    @Test
    fun aUpcAStartingWithTwoIsAnInStoreCodeToo() {
        assertTrue(scan("212345678909", BarcodeFormat.UPC_A).isInStoreCode)
        // ... also when the camera library hands it over as an EAN-13 with a leading zero
        assertTrue(scan("0212345678909", BarcodeFormat.EAN_13).isInStoreCode)
        assertFalse(scan("036000291452", BarcodeFormat.UPC_A).isInStoreCode)
    }

    @Test
    fun anEan8StartingWith20IsARealProductCode() {
        // Lidl's own brands use 20xxxxxx; Open Food Facts has them.
        assertFalse(scan("20003166", BarcodeFormat.EAN_8).isInStoreCode)
    }

    // --- manual entry ------------------------------------------------------------------------------

    @Test
    fun spacesAndHyphensInManualEntryAreIgnored() {
        assertEquals("4006381333931", manual("4006381 333931").code)
        assertEquals("4006381333931", manual("4006381-333931").code)
        assertEquals("4006381333931", manual("  4 006381 333931 ").code)
    }

    @Test
    fun manualEntryAcceptsEightTwelveThirteenAndFourteenDigits() {
        assertEquals("40084107", manual("40084107").code)
        assertEquals("036000291452", manual("036000291452").code)
        assertEquals("4006381333931", manual("4006381333931").code)
        assertEquals("04006381333931", manual("04006381333931").code)
    }

    @Test
    fun manualEntryErrors() {
        assertEquals(BarcodeError.EMPTY, manualError(""))
        assertEquals(BarcodeError.EMPTY, manualError("  - "))
        assertEquals(BarcodeError.NOT_DIGITS, manualError("4006381 33393a"))
        assertEquals(BarcodeError.NOT_DIGITS, manualError("４００６３８１３３３９３１")) // full-width digits are no digits here
        assertEquals(BarcodeError.WRONG_LENGTH, manualError("12345"))
        assertEquals(BarcodeError.WRONG_LENGTH, manualError("12345678901234567"))
        assertEquals(BarcodeError.WRONG_CHECK_DIGIT, manualError("4006381333932"))
        assertEquals(BarcodeError.WRONG_CHECK_DIGIT, manualError("40084108"))
    }

    @Test
    fun eightTypedDigitsThatAreAnEan8AreAnEan8() {
        val barcode = manual("40084107")
        assertEquals("40084107", barcode.code)
        assertTrue("40084107" in barcode.lookupCodes)
    }

    @Test
    fun eightTypedDigitsThatAreOnlyAValidUpcEAreExpanded() {
        val barcode = manual("04252614")
        assertEquals("042100005264", barcode.code)
    }

    @Test
    fun eightTypedDigitsThatAreBothAlsoTryTheUpcEReading() {
        // 04252614 is no EAN-8 (check digit), so find a code that is both: search the first one.
        val both = (0 until 100_000).map { "0" + it.toString().padStart(6, '0') }
            .flatMap { body -> (0..9).map { body + it } }
            .first { BarcodeNormalizer.hasValidCheckDigit(it) && BarcodeNormalizer.expandUpcE(it) != null }
        val barcode = manual(both)
        assertEquals(both, barcode.code) // an EAN-8 first
        assertTrue(BarcodeNormalizer.expandUpcE(both)!! in barcode.lookupCodes)
    }

    @Test
    fun lookupCodesNeverRepeatAndStartWithTheCode() {
        listOf("40084107", "036000291452", "4006381333931", "04006381333931", "0036000291452").forEach { text ->
            val barcode = manual(text)
            assertEquals(barcode.lookupCodes.distinct(), barcode.lookupCodes, text)
            assertEquals(barcode.code, barcode.lookupCodes.first(), text)
            assertTrue(barcode.lookupCodes.size <= 8, text)
        }
    }
}
