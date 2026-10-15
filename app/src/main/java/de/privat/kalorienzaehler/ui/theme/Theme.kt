package de.privat.kalorienzaehler.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theme: dynamic color on Android 12+, otherwise the palette generated from the
 * brand color #2E7D5B. Follows the system dark mode.
 */
@Composable
fun KalorienzaehlerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Amber for "over the target": calm and neutral, never alarm red (PROMPT.md §6.4). Dark enough on
 * light surfaces and light enough on dark ones to keep a contrast of at least 4.5:1.
 */
@Composable
fun overTargetColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    if (darkTheme) Color(0xFFFFB74D) else Color(0xFF8A5100)
