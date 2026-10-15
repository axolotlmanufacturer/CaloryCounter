package de.privat.kalorienzaehler.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat

/**
 * The live camera of the scanner: a preview that reads retail barcodes. The scanner screen only talks to this, so
 * tests (which have no camera) can put a stand-in in [LocalBarcodeCamera]; the real one is [CameraXBarcodeCamera].
 */
interface BarcodeCamera {

    /**
     * Shows the camera image and reports codes.
     *
     * @param scanning false pauses reading (the image stays on screen).
     * @param torchOn switches the flash light on or off.
     * @param onTorchAvailable tells whether the camera has a flash light at all (called once the camera is bound).
     * @param onCode a barcode was read, its text and format; called on the main thread, possibly many times for one
     *   code (every frame that shows it).
     */
    @Composable
    fun Preview(
        scanning: Boolean,
        torchOn: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        onCode: (text: String, format: BarcodeFormat) -> Unit,
        modifier: Modifier,
    )
}

val LocalBarcodeCamera = staticCompositionLocalOf<BarcodeCamera> { CameraXBarcodeCamera }
