package de.privat.kalorienzaehler.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.privat.kalorienzaehler.R

/** Shown for the moment it takes to read the saved profile at app start. */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading_description)
    Surface(modifier = modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
        }
    }
}
