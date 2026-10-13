package de.privat.kalorienzaehler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import de.privat.kalorienzaehler.ui.KalorienzaehlerRoot
import de.privat.kalorienzaehler.ui.theme.KalorienzaehlerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KalorienzaehlerTheme {
                KalorienzaehlerRoot()
            }
        }
    }
}
