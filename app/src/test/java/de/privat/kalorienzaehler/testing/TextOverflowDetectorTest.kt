package de.privat.kalorienzaehler.testing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The clipping detector must really detect clipping, otherwise the font scale tests prove nothing. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextOverflowDetectorTest {

    @get:Rule
    val compose = createComposeRule()

    private val longText = "Ein ziemlich langer Satz, der in eine schmale Spalte niemals vollständig hineinpasst"

    @Test
    fun aTextCutByALineLimitIsDetected() {
        compose.setContent { Text(longText, maxLines = 1, modifier = Modifier.width(80.dp)) }
        assertEquals(listOf(longText), compose.clippedTexts())
    }

    @Test
    fun aTextInABoxThatIsTooLowIsDetected() {
        compose.setContent { Text(longText, modifier = Modifier.width(80.dp).height(20.dp)) }
        assertTrue(compose.clippedTexts().isNotEmpty())
    }

    @Test
    fun textsThatFitOrWrapFreelyAreNotReported() {
        compose.setContent {
            Column {
                Text("Kurz")
                Text(longText, modifier = Modifier.width(80.dp))
            }
        }
        assertEquals(emptyList<String>(), compose.clippedTexts())
    }
}
