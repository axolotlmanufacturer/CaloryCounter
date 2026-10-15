package de.privat.kalorienzaehler.ui.scanner

import app.cash.turbine.test
import de.privat.kalorienzaehler.data.fooddb.FoodDbStatus
import de.privat.kalorienzaehler.data.repository.BarcodeLookup
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.data.repository.FoodCatalog
import de.privat.kalorienzaehler.domain.barcode.Barcode
import de.privat.kalorienzaehler.domain.barcode.BarcodeError
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.FoodSource
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.testing.FakeCustomFoodRepository
import de.privat.kalorienzaehler.testing.FakeDiaryRepository
import de.privat.kalorienzaehler.testing.FakeFavoriteRepository
import de.privat.kalorienzaehler.testing.FakeFoodRepository
import de.privat.kalorienzaehler.testing.MainDispatcherRule
import de.privat.kalorienzaehler.testing.TEST_CLOCK
import de.privat.kalorienzaehler.testing.TEST_TODAY
import de.privat.kalorienzaehler.testing.testFood
import de.privat.kalorienzaehler.testing.testOff
import de.privat.kalorienzaehler.ui.navigation.ScannerRoute
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScannerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val custom = FakeCustomFoodRepository()
    private val database = FakeFoodRepository()
    private val catalog = DefaultFoodCatalog(database, custom, FakeFavoriteRepository(), FakeDiaryRepository())

    private fun viewModel(catalog: FoodCatalog = this.catalog) =
        ScannerViewModel(ScannerRoute(TEST_TODAY.toEpochDay(), Meal.MITTAGESSEN), catalog, TEST_CLOCK)

    private val cola = testOff("5449000000996", "Cola", "Coca-Cola", kcal = 42.0)

    @Test
    fun startsScanningForTheGivenMealAndDay() {
        val viewModel = viewModel()
        assertEquals(ScannerPhase.Scanning, viewModel.phase)
        assertEquals(Meal.MITTAGESSEN, viewModel.meal)
        assertEquals(TEST_TODAY, viewModel.date)
        assertEquals("", viewModel.manualText)
        assertNull(viewModel.manualError)
        assertFalse(viewModel.torchOn)
    }

    @Test
    fun opensTheFoodDatabaseWhileTheCameraStarts() {
        viewModel()
        assertEquals(1, database.prepareCalls)
    }

    @Test
    fun aCodeOfTheDatabaseOpensTheFoodAndVibrates() = runTest {
        database.preset(cola)
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("5449000000996", BarcodeFormat.EAN_13)
            assertEquals(ScannerEvent.Detected, awaitItem())
            assertEquals(ScannerEvent.FoodFound(cola.key), awaitItem())
        }
    }

    @Test
    fun ownFoodsComeBeforeTheDatabase() = runTest {
        database.preset(cola)
        val (mine) = custom.preset(testFood(name = "Meine Cola", barcode = "5449000000996"))
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("5449000000996", BarcodeFormat.EAN_13)
            assertEquals(ScannerEvent.Detected, awaitItem())
            assertEquals(ScannerEvent.FoodFound(FoodKey(FoodSource.CUSTOM, mine.id.toString())), awaitItem())
        }
    }

    @Test
    fun aUpcAIsFoundUnderItsEan13Spelling() = runTest {
        // Open Food Facts stores many UPC-A codes with a leading zero.
        val bar = testOff("0036000291452", "Riegel")
        database.preset(bar)
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("036000291452", BarcodeFormat.UPC_A)
            skipItems(1)
            assertEquals(ScannerEvent.FoodFound(bar.key), awaitItem())
        }
    }

    @Test
    fun aUpcEIsExpandedBeforeTheLookup() = runTest {
        val bar = testOff("042100005264", "Kaugummi")
        database.preset(bar)
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("04252614", BarcodeFormat.UPC_E)
            skipItems(1)
            assertEquals(ScannerEvent.FoodFound(bar.key), awaitItem())
        }
    }

    @Test
    fun aMisreadIsIgnoredAndTheCameraKeepsScanning() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("4006381333932", BarcodeFormat.EAN_13) // wrong check digit
            viewModel.onCameraCode("123", BarcodeFormat.EAN_13)
            expectNoEvents()
        }
        assertEquals(ScannerPhase.Scanning, viewModel.phase)
    }

    @Test
    fun anUnknownCodeIsNotFoundAndOffersToCreateIt() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraCode("4006381333931", BarcodeFormat.EAN_13)
        val phase = viewModel.phase as ScannerPhase.NotFound
        assertEquals("4006381333931", phase.barcode.code)
        assertFalse(phase.barcode.isInStoreCode)
        assertFalse(phase.databaseUnavailable)
    }

    @Test
    fun inStoreCodesAreFlaggedWhenNothingHasThem() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraCode("2012345678903", BarcodeFormat.EAN_13)
        assertTrue((viewModel.phase as ScannerPhase.NotFound).barcode.isInStoreCode)
    }

    @Test
    fun anInStoreCodeOfAnOwnFoodIsStillFound() = runTest {
        val (mine) = custom.preset(testFood(name = "Frischer Spargel", barcode = "2012345678903"))
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("2012345678903", BarcodeFormat.EAN_13)
            skipItems(1)
            assertEquals(ScannerEvent.FoodFound(FoodKey(FoodSource.CUSTOM, mine.id.toString())), awaitItem())
        }
    }

    @Test
    fun aFailedDatabaseIsMentionedWithTheNotFoundResult() = runTest {
        database.setStatus(FoodDbStatus.Failed)
        val viewModel = viewModel()
        viewModel.onCameraCode("4006381333931", BarcodeFormat.EAN_13)
        assertTrue((viewModel.phase as ScannerPhase.NotFound).databaseUnavailable)
    }

    @Test
    fun aLookupThatBreaksLeavesTheWayToCreateTheFood() = runTest {
        val failing = object : FoodCatalog by catalog {
            override suspend fun findByBarcode(barcode: Barcode): BarcodeLookup = throw IOException("disk")
        }
        val viewModel = viewModel(failing)
        viewModel.onCameraCode("4006381333931", BarcodeFormat.EAN_13)
        assertTrue((viewModel.phase as ScannerPhase.NotFound).databaseUnavailable)
    }

    @Test
    fun framesThatKeepShowingTheCodeDuringTheLookupAreIgnored() = runTest {
        database.setStatus(FoodDbStatus.Preparing) // the lookup waits for the database
        database.preset(cola)
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onCameraCode("5449000000996", BarcodeFormat.EAN_13)
            assertEquals(ScannerEvent.Detected, awaitItem())
            assertTrue(viewModel.phase is ScannerPhase.Looking)
            repeat(5) { viewModel.onCameraCode("5449000000996", BarcodeFormat.EAN_13) }
            viewModel.onCameraCode("4006381333931", BarcodeFormat.EAN_13) // another product in the frame
            database.setStatus(FoodDbStatus.Ready)
            assertEquals(ScannerEvent.FoodFound(cola.key), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun scanAgainReturnsToTheCamera() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraCode("4006381333931", BarcodeFormat.EAN_13)
        assertTrue(viewModel.phase is ScannerPhase.NotFound)
        viewModel.scanAgain()
        assertEquals(ScannerPhase.Scanning, viewModel.phase)
        database.preset(cola)
        viewModel.events.test {
            assertEquals(ScannerEvent.Detected, awaitItem()) // the first, unsuccessful scan
            viewModel.onCameraCode("5449000000996", BarcodeFormat.EAN_13)
            assertEquals(ScannerEvent.Detected, awaitItem())
            assertEquals(ScannerEvent.FoodFound(cola.key), awaitItem())
        }
    }

    // --- typing the code ---------------------------------------------------------------------------

    @Test
    fun aTypedCodeIsLookedUpWithoutVibration() = runTest {
        database.preset(cola)
        val viewModel = viewModel()
        viewModel.onManualTextChange("5449 0000 00996")
        viewModel.events.test {
            viewModel.submitManual()
            assertEquals(ScannerEvent.FoodFound(cola.key), awaitItem())
        }
    }

    @Test
    fun aTypoInTheCheckDigitIsExplainedAndNothingIsLookedUp() = runTest {
        database.preset(cola)
        val viewModel = viewModel()
        viewModel.onManualTextChange("5449000000997")
        viewModel.submitManual()
        assertEquals(BarcodeError.WRONG_CHECK_DIGIT, viewModel.manualError)
        assertEquals(ScannerPhase.Scanning, viewModel.phase)
    }

    @Test
    fun manualErrorsForLettersAndLength() {
        val viewModel = viewModel()
        viewModel.onManualTextChange("54490000009x6")
        viewModel.submitManual()
        assertEquals(BarcodeError.NOT_DIGITS, viewModel.manualError)
        viewModel.onManualTextChange("12345")
        viewModel.submitManual()
        assertEquals(BarcodeError.WRONG_LENGTH, viewModel.manualError)
    }

    @Test
    fun anEmptyFieldDoesNotNag() {
        val viewModel = viewModel()
        viewModel.submitManual()
        assertNull(viewModel.manualError)
        assertEquals(ScannerPhase.Scanning, viewModel.phase)
    }

    @Test
    fun typingAgainClearsTheError() {
        val viewModel = viewModel()
        viewModel.onManualTextChange("12345")
        viewModel.submitManual()
        assertEquals(BarcodeError.WRONG_LENGTH, viewModel.manualError)
        viewModel.onManualTextChange("123456")
        assertNull(viewModel.manualError)
    }

    @Test
    fun anotherCodeCanBeTypedAfterNothingWasFound() = runTest {
        val viewModel = viewModel()
        viewModel.onManualTextChange("4006381333931")
        viewModel.submitManual()
        assertTrue(viewModel.phase is ScannerPhase.NotFound)
        database.preset(cola)
        viewModel.onManualTextChange("5449000000996")
        viewModel.events.test {
            viewModel.submitManual()
            assertEquals(ScannerEvent.FoodFound(cola.key), awaitItem())
        }
    }

    @Test
    fun nothingCanBeTypedWhileACodeIsBeingLookedUp() = runTest {
        database.setStatus(FoodDbStatus.Preparing)
        val viewModel = viewModel()
        viewModel.onCameraCode("4006381333931", BarcodeFormat.EAN_13)
        val looking = viewModel.phase
        assertTrue(looking is ScannerPhase.Looking)
        viewModel.onManualTextChange("5449000000996")
        viewModel.submitManual()
        assertEquals(looking, viewModel.phase)
    }

    // --- torch -------------------------------------------------------------------------------------

    @Test
    fun theTorchNeedsAFlashLight() {
        val viewModel = viewModel()
        viewModel.toggleTorch()
        assertFalse(viewModel.torchOn)
        viewModel.onTorchAvailable(true)
        viewModel.toggleTorch()
        assertTrue(viewModel.torchOn)
        viewModel.toggleTorch()
        assertFalse(viewModel.torchOn)
    }

    @Test
    fun theTorchGoesOffWhenTheCameraHasNoFlashLight() {
        val viewModel = viewModel()
        viewModel.onTorchAvailable(true)
        viewModel.toggleTorch()
        viewModel.onTorchAvailable(false)
        assertFalse(viewModel.torchOn)
        assertFalse(viewModel.torchAvailable)
    }
}
