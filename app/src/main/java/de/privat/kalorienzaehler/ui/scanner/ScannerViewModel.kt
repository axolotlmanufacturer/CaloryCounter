package de.privat.kalorienzaehler.ui.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.privat.kalorienzaehler.data.repository.BarcodeLookup
import de.privat.kalorienzaehler.data.repository.FoodCatalog
import de.privat.kalorienzaehler.domain.barcode.Barcode
import de.privat.kalorienzaehler.domain.barcode.BarcodeError
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat
import de.privat.kalorienzaehler.domain.barcode.BarcodeNormalizer
import de.privat.kalorienzaehler.domain.barcode.BarcodeParse
import de.privat.kalorienzaehler.domain.diary.FoodKey
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.ui.navigation.ScannerRoute
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Where the scanner is: reading, looking a code up, or showing that nothing has this code. */
sealed interface ScannerPhase {
    /** The camera reads codes (and the code can be typed). */
    data object Scanning : ScannerPhase

    /** A valid code was read and is being looked up. */
    data class Looking(val barcode: Barcode) : ScannerPhase

    /** Nothing has this code. [databaseUnavailable]: only the own foods could be searched. */
    data class NotFound(val barcode: Barcode, val databaseUnavailable: Boolean) : ScannerPhase
}

sealed interface ScannerEvent {
    /** The camera read a valid code (a short vibration confirms it). */
    data object Detected : ScannerEvent

    /** The code belongs to this food: continue with the amount. */
    data class FoodFound(val key: FoodKey) : ScannerEvent
}

/**
 * Scanning a barcode for one meal of one day (PROMPT.md 7.5): codes from the camera or typed by hand are checked
 * (check digit, UPC-E), looked up in the own foods and then in the food database, and either lead to the amount
 * screen or to "Produkt nicht gefunden". The camera and the permission live in the screen; this holds the logic.
 */
@HiltViewModel(assistedFactory = ScannerViewModel.Factory::class)
class ScannerViewModel @AssistedInject constructor(
    @Assisted route: ScannerRoute,
    private val catalog: FoodCatalog,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ScannerRoute): ScannerViewModel
    }

    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: Meal = route.meal
    val today: LocalDate get() = LocalDate.now(clock)

    var phase: ScannerPhase by mutableStateOf(ScannerPhase.Scanning)
        private set

    /** The text in the manual entry field. */
    var manualText by mutableStateOf("")
        private set

    var manualError: BarcodeError? by mutableStateOf(null)
        private set

    var torchOn by mutableStateOf(false)
        private set

    /** Whether the camera has a flash unit (reported by the camera layer). */
    var torchAvailable by mutableStateOf(false)
        private set

    private val eventChannel = Channel<ScannerEvent>(Channel.BUFFERED)
    val events: Flow<ScannerEvent> = eventChannel.receiveAsFlow()

    init {
        // The food database opens while the camera starts, so a lookup does not have to wait for it.
        catalog.prepare()
    }

    /** A code read by the camera. Frames keep arriving while a lookup runs: only the first valid code counts. */
    fun onCameraCode(text: String, format: BarcodeFormat) {
        if (phase != ScannerPhase.Scanning) return
        val parse = BarcodeNormalizer.fromScan(text, format)
        if (parse !is BarcodeParse.Valid) return // a misread: just try the next frame
        eventChannel.trySend(ScannerEvent.Detected)
        lookUp(parse.barcode)
    }

    fun onManualTextChange(text: String) {
        manualText = text
        manualError = null
    }

    fun submitManual() {
        if (phase is ScannerPhase.Looking) return
        when (val parse = BarcodeNormalizer.fromManualEntry(manualText)) {
            is BarcodeParse.Invalid -> manualError = parse.error.takeIf { it != BarcodeError.EMPTY }
            is BarcodeParse.Valid -> {
                manualError = null
                lookUp(parse.barcode)
            }
        }
    }

    fun scanAgain() {
        phase = ScannerPhase.Scanning
    }

    fun onTorchAvailable(available: Boolean) {
        torchAvailable = available
        if (!available) torchOn = false
    }

    fun toggleTorch() {
        if (torchAvailable) torchOn = !torchOn
    }

    private fun lookUp(barcode: Barcode) {
        phase = ScannerPhase.Looking(barcode)
        viewModelScope.launch {
            val result = try {
                catalog.findByBarcode(barcode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A lookup that fails is not "nothing found" for the food database, but the user can still create the food.
                BarcodeLookup.NotFound(databaseUnavailable = true)
            }
            when (result) {
                is BarcodeLookup.Found -> eventChannel.send(ScannerEvent.FoodFound(result.food.key))
                is BarcodeLookup.NotFound -> phase = ScannerPhase.NotFound(barcode, result.databaseUnavailable)
            }
        }
    }
}
