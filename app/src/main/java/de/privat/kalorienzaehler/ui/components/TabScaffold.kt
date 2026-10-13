package de.privat.kalorienzaehler.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Scaffold with a top app bar for the content of one bottom-navigation tab. */
// TopAppBar is still @ExperimentalMaterial3Api in material3 1.4.0 (verified by the compiler).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    TabScaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        modifier = modifier,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/** The same scaffold with a custom top bar (the date switcher of Heute). */
@Composable
fun TabScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}
