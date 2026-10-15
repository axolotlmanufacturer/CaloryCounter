package de.privat.kalorienzaehler.ui.scanner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.barcode.Barcode
import de.privat.kalorienzaehler.domain.barcode.BarcodeError
import de.privat.kalorienzaehler.testing.FakeBarcodeCamera
import de.privat.kalorienzaehler.testing.clippedTexts
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.testing.textsWithBrokenWords
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PROMPT.md section 8: the scanner at 200 % font size on a small phone, every state, nothing clipped. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 2.0f, qualifiers = "+w360dp-h640dp-xxhdpi")
class ScannerFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() = FakeBarcodeCamera.reset()

    private val code = Barcode("4006381333931", listOf("4006381333931"), isInStoreCode = false)
    private val inStoreCode = Barcode("2012345678903", listOf("2012345678903"), isInStoreCode = true)

    private fun show(
        access: CameraAccess = CameraAccess.AVAILABLE,
        phase: ScannerPhase = ScannerPhase.Scanning,
        torchOn: Boolean = false,
        manualText: String = "",
        manualError: BarcodeError? = null,
    ) {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBarcodeCamera provides FakeBarcodeCamera) {
                    ScannerContent(
                        subtitle = "Abendessen · Dienstag, 13.10.2026",
                        access = access,
                        phase = phase,
                        torchOn = torchOn,
                        torchAvailable = true,
                        manualText = manualText,
                        manualError = manualError,
                        onBack = {},
                        onRequestPermission = {},
                        onOpenSettings = {},
                        onCameraCode = { _, _ -> },
                        onTorchAvailable = {},
                        onToggleTorch = {},
                        onManualTextChange = {},
                        onManualSearch = {},
                        onScanAgain = {},
                        onCreateCustom = {},
                    )
                }
            }
        }
    }

    private fun assertNothingClipped(label: String) {
        assertEquals("clipped texts on $label", emptyList<String>(), compose.clippedTexts())
        assertEquals("words broken across lines on $label", emptyList<String>(), compose.textsWithBrokenWords())
    }

    /** Scrolls the text into view (the screen scrolls at this size), checks it is there, then checks the whole screen. */
    private fun assertShownAndFits(text: String, label: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        assertNothingClipped(label)
    }

    @Test
    fun theCameraWithItsHintAndTheManualEntryFit() {
        show(manualText = "4006381333931")
        compose.onNodeWithText(string(R.string.scanner_aim_hint)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.scanner_torch_on)).assertIsDisplayed()
        assertNothingClipped("Kamera")
        assertShownAndFits(string(R.string.scanner_manual_search), "Kamera, Eingabe")
    }

    @Test
    fun theRationaleBeforeAskingFits() {
        show(access = CameraAccess.ASK)
        assertShownAndFits(string(R.string.scanner_permission_allow), "Kamera erlauben?")
        assertShownAndFits(string(R.string.scanner_manual_search), "Kamera erlauben?, Eingabe")
    }

    @Test
    fun theFinalRefusalFits() {
        show(access = CameraAccess.DENIED)
        assertShownAndFits(string(R.string.scanner_permission_settings), "Kamera abgelehnt")
    }

    @Test
    fun theMissingCameraFits() {
        show(access = CameraAccess.NO_CAMERA)
        assertShownAndFits(string(R.string.scanner_no_camera), "keine Kamera")
    }

    @Test
    fun theLookupFits() {
        show(phase = ScannerPhase.Looking(code))
        assertShownAndFits(string(R.string.scanner_looking, code.code), "Suche")
    }

    @Test
    fun nothingFoundWithAllHintsFits() {
        show(phase = ScannerPhase.NotFound(inStoreCode, databaseUnavailable = true))
        assertShownAndFits(string(R.string.scanner_not_found_title), "Nicht gefunden")
        assertShownAndFits(string(R.string.scanner_in_store_hint), "Nicht gefunden, Etikett")
        assertShownAndFits(string(R.string.scanner_database_unavailable), "Nicht gefunden, Datenbank")
        assertShownAndFits(string(R.string.scanner_create_custom), "Nicht gefunden, Anlegen")
        assertShownAndFits(string(R.string.scanner_scan_again), "Nicht gefunden, Nochmal")
    }

    @Test
    fun theCheckDigitErrorFits() {
        show(access = CameraAccess.NO_CAMERA, manualText = "4006381333932", manualError = BarcodeError.WRONG_CHECK_DIGIT)
        assertShownAndFits(string(R.string.scanner_error_check_digit), "Prüfziffer")
    }

    @Test
    fun theLengthErrorFits() {
        show(access = CameraAccess.NO_CAMERA, manualText = "12345", manualError = BarcodeError.WRONG_LENGTH)
        assertShownAndFits(string(R.string.scanner_error_length), "Länge")
    }

    @Test
    fun theLettersErrorFits() {
        show(access = CameraAccess.NO_CAMERA, manualText = "40063x", manualError = BarcodeError.NOT_DIGITS)
        assertShownAndFits(string(R.string.scanner_error_digits), "Buchstaben")
    }
}
