package de.privat.kalorienzaehler.testing

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat
import de.privat.kalorienzaehler.ui.scanner.BarcodeCamera

/**
 * A camera for tests: it shows buttons that "read" a code, and records what the scanner asked of it. A real camera
 * cannot run on the JVM.
 */
object FakeBarcodeCamera : BarcodeCamera {

    /** The codes the buttons deliver: label -> (text, format). Set by the test before the scanner opens. */
    var codes: List<Triple<String, String, BarcodeFormat>> = emptyList()

    /** Whether the fake camera has a flash light. */
    var hasTorch = true

    /** The last values the scanner passed in (for assertions). */
    var lastScanning: Boolean? = null
        private set
    var lastTorchOn: Boolean? = null
        private set

    fun reset() {
        codes = emptyList()
        hasTorch = true
        lastScanning = null
        lastTorchOn = null
    }

    @Composable
    override fun Preview(
        scanning: Boolean,
        torchOn: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        onCode: (text: String, format: BarcodeFormat) -> Unit,
        modifier: Modifier,
    ) {
        lastScanning = scanning
        lastTorchOn = torchOn
        LaunchedEffect(Unit) { onTorchAvailable(hasTorch) }
        Column(modifier) {
            Text("Kamera-Attrappe")
            codes.forEach { (label, text, format) ->
                Button(onClick = { onCode(text, format) }) { Text(label) }
            }
        }
    }
}
