package de.privat.kalorienzaehler.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.barcode.Barcode
import de.privat.kalorienzaehler.domain.barcode.BarcodeError
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat
import de.privat.kalorienzaehler.testing.FakeBarcodeCamera
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+w411dp-h891dp-xxhdpi")
class ScannerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val code = Barcode("4006381333931", listOf("4006381333931"), isInStoreCode = false)

    private val calls = mutableListOf<String>()

    @Before
    fun setUp() {
        FakeBarcodeCamera.reset()
    }

    @After
    fun tearDown() = FakeBarcodeCamera.reset()

    private fun show(
        access: CameraAccess = CameraAccess.AVAILABLE,
        phase: ScannerPhase = ScannerPhase.Scanning,
        torchOn: Boolean = false,
        torchAvailable: Boolean = true,
        manualText: String = "",
        manualError: BarcodeError? = null,
    ) {
        compose.setContent {
            KalorienzaehlerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBarcodeCamera provides FakeBarcodeCamera) {
                    ScannerContent(
                        subtitle = "Mittagessen · Heute",
                        access = access,
                        phase = phase,
                        torchOn = torchOn,
                        torchAvailable = torchAvailable,
                        manualText = manualText,
                        manualError = manualError,
                        onBack = { calls += "back" },
                        onRequestPermission = { calls += "request" },
                        onOpenSettings = { calls += "settings" },
                        onCameraCode = { text, format -> calls += "code $text $format" },
                        onTorchAvailable = {},
                        onToggleTorch = { calls += "torch" },
                        onManualTextChange = { calls += "typed $it" },
                        onManualSearch = { calls += "search" },
                        onScanAgain = { calls += "again" },
                        onCreateCustom = { calls += "create $it" },
                    )
                }
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = string(id, *args)

    // --- camera and permission ---------------------------------------------------------------------

    @Test
    fun theCameraRunsWhenThePermissionIsGranted() {
        FakeBarcodeCamera.codes = listOf(Triple("Lies Cola", "5449000000996", BarcodeFormat.EAN_13))
        show()
        compose.onNodeWithText("Kamera-Attrappe").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_aim_hint)).assertIsDisplayed()
        compose.onNodeWithText("Lies Cola").performClick()
        assertEquals(listOf("code 5449000000996 EAN_13"), calls)
        compose.onNodeWithText(text(R.string.scanner_permission_allow)).assertDoesNotExist()
    }

    @Test
    fun beforeAskingTheAppExplainsWhyItNeedsTheCamera() {
        show(access = CameraAccess.ASK)
        compose.onNodeWithText(text(R.string.scanner_permission_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_permission_rationale)).assertIsDisplayed()
        compose.onNodeWithText("Kamera-Attrappe").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.scanner_permission_allow)).performClick()
        assertEquals(listOf("request"), calls)
    }

    @Test
    fun aFinalRefusalPointsToTheSettingsAndTheManualEntry() {
        show(access = CameraAccess.DENIED)
        compose.onNodeWithText(text(R.string.scanner_permission_denied)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_permission_settings)).performClick()
        assertEquals(listOf("settings"), calls)
        compose.onNodeWithText(text(R.string.scanner_manual_label)).assertIsDisplayed()
    }

    @Test
    fun withoutACameraOnlyTheManualEntryRemains() {
        show(access = CameraAccess.NO_CAMERA)
        compose.onNodeWithText(text(R.string.scanner_no_camera)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_manual_label)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_permission_allow)).assertDoesNotExist()
    }

    @Test
    fun theCameraIsPausedWhileACodeIsLookedUpOrNothingWasFound() {
        show(phase = ScannerPhase.Looking(code))
        compose.waitForIdle()
        assertEquals(false, FakeBarcodeCamera.lastScanning)
    }

    @Test
    fun theCameraScansWhileScanning() {
        show()
        compose.waitForIdle()
        assertEquals(true, FakeBarcodeCamera.lastScanning)
    }

    // --- torch -------------------------------------------------------------------------------------

    @Test
    fun theTorchButtonSaysWhatATapDoes() {
        show(torchOn = false)
        compose.onNodeWithContentDescription(text(R.string.scanner_torch_on)).performClick()
        assertEquals(listOf("torch"), calls)
    }

    @Test
    fun whenTheTorchIsOnTheButtonOffersToSwitchItOff() {
        show(torchOn = true)
        compose.onNodeWithContentDescription(text(R.string.scanner_torch_off)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.scanner_torch_on)).assertDoesNotExist()
    }

    @Test
    fun noTorchButtonWithoutAFlashLight() {
        show(torchAvailable = false)
        compose.onNodeWithContentDescription(text(R.string.scanner_torch_on)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.scanner_torch_off)).assertDoesNotExist()
    }

    // --- results -----------------------------------------------------------------------------------

    @Test
    fun whileLookingTheCodeIsShown() {
        show(phase = ScannerPhase.Looking(code))
        compose.onNodeWithText(text(R.string.scanner_looking, code.code)).assertIsDisplayed()
    }

    @Test
    fun nothingFoundOffersToCreateTheFoodWithTheCode() {
        show(phase = ScannerPhase.NotFound(code, databaseUnavailable = false))
        compose.onNodeWithText(text(R.string.scanner_not_found_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_not_found_body, code.code)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_in_store_hint)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.scanner_database_unavailable)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.scanner_create_custom)).performClick()
        compose.onNodeWithText(text(R.string.scanner_scan_again)).performClick()
        assertEquals(listOf("create ${code.code}", "again"), calls)
    }

    @Test
    fun inStoreCodesGetAHint() {
        val inStore = Barcode("2012345678903", listOf("2012345678903"), isInStoreCode = true)
        show(phase = ScannerPhase.NotFound(inStore, databaseUnavailable = false))
        compose.onNodeWithText(text(R.string.scanner_in_store_hint)).assertIsDisplayed()
    }

    @Test
    fun anUnavailableDatabaseIsMentioned() {
        show(phase = ScannerPhase.NotFound(code, databaseUnavailable = true))
        compose.onNodeWithText(text(R.string.scanner_database_unavailable)).assertIsDisplayed()
    }

    @Test
    fun withoutACameraThereIsNothingToScanAgain() {
        show(access = CameraAccess.NO_CAMERA, phase = ScannerPhase.NotFound(code, databaseUnavailable = false))
        compose.onNodeWithText(text(R.string.scanner_scan_again)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.scanner_create_custom)).assertIsDisplayed()
    }

    // --- typing the code ---------------------------------------------------------------------------

    @Test
    fun typingAndSearching() {
        show(manualText = "")
        compose.onNodeWithText(text(R.string.scanner_manual_label)).performTextInput("4006381333931")
        compose.onNodeWithText(text(R.string.scanner_manual_search)).assertIsNotEnabled() // the text comes from the view model
        assertEquals(listOf("typed 4006381333931"), calls)
    }

    @Test
    fun theKeyboardsSearchKeySearchesToo() {
        show(manualText = "4006381333931")
        compose.onNodeWithText(text(R.string.scanner_manual_label)).performImeAction()
        assertEquals(listOf("search"), calls)
    }

    @Test
    fun theSearchButtonWaitsForSomeText() {
        show(manualText = "")
        compose.onNodeWithText(text(R.string.scanner_manual_search)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.scanner_manual_search)).assertIsDisplayed()
    }

    @Test
    fun theSearchButtonIsEnabledWithText() {
        show(manualText = "4006381333931")
        compose.onNodeWithText(text(R.string.scanner_manual_search)).assertIsEnabled()
    }

    @Test
    fun aTypoInTheCheckDigitIsExplained() {
        show(manualText = "4006381333932", manualError = BarcodeError.WRONG_CHECK_DIGIT)
        compose.onNodeWithText(text(R.string.scanner_error_check_digit)).assertIsDisplayed()
    }

    @Test
    fun lettersAreExplained() {
        show(manualText = "40063x", manualError = BarcodeError.NOT_DIGITS)
        compose.onNodeWithText(text(R.string.scanner_error_digits)).assertIsDisplayed()
    }

    @Test
    fun aWrongLengthIsExplained() {
        show(manualText = "12345", manualError = BarcodeError.WRONG_LENGTH)
        compose.onNodeWithText(text(R.string.scanner_error_length)).assertIsDisplayed()
    }

    @Test
    fun theFieldIsLockedWhileACodeIsLookedUp() {
        show(phase = ScannerPhase.Looking(code), manualText = "4006381333931")
        compose.onNodeWithText(text(R.string.scanner_manual_search)).assertIsNotEnabled()
        assertFalse(calls.contains("search"))
    }
}
