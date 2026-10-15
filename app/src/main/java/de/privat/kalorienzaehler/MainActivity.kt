package de.privat.kalorienzaehler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import de.privat.kalorienzaehler.ui.AppRoot
import de.privat.kalorienzaehler.ui.scanner.BarcodeCamera
import de.privat.kalorienzaehler.ui.scanner.LocalBarcodeCamera
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var barcodeCamera: BarcodeCamera

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KalorienzaehlerTheme {
                CompositionLocalProvider(LocalBarcodeCamera provides barcodeCamera) {
                    AppRoot()
                }
            }
        }
    }
}
