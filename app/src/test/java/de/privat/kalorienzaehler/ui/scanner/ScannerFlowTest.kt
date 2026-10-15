package de.privat.kalorienzaehler.ui.scanner

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.privat.kalorienzaehler.MainActivity
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.weight.WeightPoint
import de.privat.kalorienzaehler.testing.FakeBarcodeCamera
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeFoodRepository
import de.privat.kalorienzaehler.testing.FakeProfileRepository
import de.privat.kalorienzaehler.testing.FakeWeightRepository
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.sampleProfile
import de.privat.kalorienzaehler.testing.string
import de.privat.kalorienzaehler.testing.testOff
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** From the add screen to a logged food with the barcode scanner (PROMPT.md 7.5 and the definition of done in 13). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "+w411dp-h891dp-xxhdpi")
class ScannerFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var profiles: FakeProfileRepository

    @Inject lateinit var weights: FakeWeightRepository

    @Inject lateinit var diary: FakeDiaryRepository

    @Inject lateinit var customFoods: FakeCustomFoodRepository

    @Inject lateinit var database: FakeFoodRepository

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hilt.inject()
        profiles.preset(sampleProfile())
        weights.preset(WeightPoint(TEST_TODAY, 80.0))
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        FakeBarcodeCamera.reset()
    }

    @After
    fun tearDown() = FakeBarcodeCamera.reset()

    private fun text(id: Int, vararg args: Any) = string(id, *args)

    private fun openScanner() {
        compose.onNodeWithContentDescription(text(R.string.today_add_to_meal, text(R.string.meal_lunch))).performClick()
        compose.onNodeWithContentDescription(text(R.string.add_scan_barcode)).performClick()
        compose.onNodeWithText(text(R.string.scanner_title)).assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun aScannedProductGoesStraightToTheAmountAndIsLogged() {
        database.preset(testOff("5449000000996", "Cola Zero", "Coca-Cola", kcal = 0.5))
        FakeBarcodeCamera.codes = listOf(Triple("Lies die Cola", "5449000000996", BarcodeFormat.EAN_13))
        openScanner()

        compose.onNodeWithText("Lies die Cola").performClick()
        waitForText(text(R.string.portion_title))
        compose.onNodeWithText("Cola Zero").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.portion_amount)).performTextReplacement("330")
        compose.onNodeWithText(text(R.string.portion_add)).performClick()

        compose.onNode(hasText(text(R.string.nav_today)) and isSelectable()).assertIsDisplayed()
        val entry = diary.stored.single()
        assertEquals(FoodSource.OFF, entry.source)
        assertEquals("5449000000996", entry.foodRef)
        assertEquals("Cola Zero", entry.name)
        assertEquals(330.0, entry.grams, 0.0)
    }

    @Test
    fun theAmountScreenGoesBackToTheAddScreenNotToTheCamera() {
        database.preset(testOff("5449000000996", "Cola Zero", "Coca-Cola"))
        FakeBarcodeCamera.codes = listOf(Triple("Lies die Cola", "5449000000996", BarcodeFormat.EAN_13))
        openScanner()
        compose.onNodeWithText("Lies die Cola").performClick()
        waitForText(text(R.string.portion_title))

        pressBack()
        compose.onNodeWithText(text(R.string.add_search_label)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_title)).assertDoesNotExist()
    }

    @Test
    fun anUnknownProductCanBeCreatedWithItsBarcodeAndIsFoundTheNextTime() {
        FakeBarcodeCamera.codes = listOf(Triple("Lies den Riegel", "4006381333931", BarcodeFormat.EAN_13))
        openScanner()

        compose.onNodeWithText("Lies den Riegel").performClick()
        waitForText(text(R.string.scanner_not_found_title))
        compose.onNodeWithText(text(R.string.scanner_not_found_body, "4006381333931")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_create_custom)).performClick()

        // The form opens with the code filled in; name and energy are all that is missing.
        compose.onNodeWithText("4006381333931").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.custom_name)).performTextInput("Müsliriegel Nuss")
        compose.onNodeWithText(text(R.string.field_energy_kcal)).performScrollTo().performTextInput("420")
        compose.onNodeWithText(text(R.string.action_save)).performClick()

        waitForText(text(R.string.portion_title))
        compose.onNodeWithText(text(R.string.portion_add)).performClick()
        assertEquals("Müsliriegel Nuss", diary.stored.single().name)
        assertEquals("4006381333931", customFoods.stored.single().barcode)

        // Scanning the same product again now finds the own food (own foods come first).
        openScanner()
        compose.onNodeWithText("Lies den Riegel").performClick()
        waitForText(text(R.string.portion_title))
        compose.onNodeWithText("Müsliriegel Nuss").assertIsDisplayed()
    }

    @Test
    fun nothingFoundCanBeScannedAgain() {
        database.preset(testOff("5449000000996", "Cola Zero", "Coca-Cola"))
        FakeBarcodeCamera.codes = listOf(
            Triple("Lies Unbekanntes", "4006381333931", BarcodeFormat.EAN_13),
            Triple("Lies die Cola", "5449000000996", BarcodeFormat.EAN_13),
        )
        openScanner()
        compose.onNodeWithText("Lies Unbekanntes").performClick()
        waitForText(text(R.string.scanner_not_found_title))
        compose.onNodeWithText(text(R.string.scanner_scan_again)).performClick()
        compose.onNodeWithText(text(R.string.scanner_not_found_title)).assertDoesNotExist()

        compose.onNodeWithText("Lies die Cola").performClick()
        waitForText(text(R.string.portion_title))
        compose.onNodeWithText("Cola Zero").assertIsDisplayed()
    }

    @Test
    fun anInStoreCodeExplainsWhyItIsNotInAnyDatabase() {
        FakeBarcodeCamera.codes = listOf(Triple("Lies das Etikett", "2012345678903", BarcodeFormat.EAN_13))
        openScanner()
        compose.onNodeWithText("Lies das Etikett").performClick()
        waitForText(text(R.string.scanner_not_found_title))
        compose.onNodeWithText(text(R.string.scanner_in_store_hint)).assertIsDisplayed()
    }

    @Test
    fun aCodeCanBeTypedWhenTheCameraIsNotAllowed() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        database.preset(testOff("5449000000996", "Cola Zero", "Coca-Cola"))
        openScanner()
        compose.onNodeWithText(text(R.string.scanner_permission_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_permission_rationale)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.scanner_manual_label)).performTextInput("5449000000996")
        compose.onNodeWithText(text(R.string.scanner_manual_search)).performClick()
        waitForText(text(R.string.portion_title))
        compose.onNodeWithText("Cola Zero").assertIsDisplayed()
    }

    @Test
    fun aTypoInTheTypedCodeIsExplainedOnTheSpot() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        openScanner()
        compose.onNodeWithText(text(R.string.scanner_manual_label)).performTextInput("5449000000997")
        compose.onNodeWithText(text(R.string.scanner_manual_search)).performClick()
        compose.onNodeWithText(text(R.string.scanner_error_check_digit)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_not_found_title)).assertDoesNotExist()
    }

    @Test
    fun theScannerWorksWithoutACameraAtAll() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        openScanner()
        compose.onNodeWithText(text(R.string.scanner_no_camera)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scanner_manual_label)).assertIsDisplayed()
    }

    @Test
    fun theBackArrowLeavesTheScanner() {
        openScanner()
        pressBack()
        compose.onNodeWithText(text(R.string.add_search_label)).assertIsDisplayed()
    }
}
