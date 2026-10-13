package de.privat.kalorienzaehler.testing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode

/**
 * Texts that are cut off: higher than their box, shortened by a line limit, or running past the
 * left or right edge of the screen. At 200 % font size nothing may be clipped (PROMPT.md §8), so
 * tests assert that this list is empty.
 *
 * Needs `@GraphicsMode(NATIVE)`: with Robolectric's legacy graphics text has no real metrics.
 * (`TextLayoutResult.hasVisualOverflow` is deliberately not used: it also reports every
 * shrink-wrapped single-line text, because the paragraph width is the available width.)
 */
fun ComposeContentTestRule.clippedTexts(): List<String> {
    val offenders = mutableListOf<String>()
    val screen = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
    val rootWidth = onRoot().fetchSemanticsNode().boundsInRoot.right
    screen.forEach { node ->
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: return@forEach
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val cutOff = layouts.any { it.didOverflowHeight || it.multiParagraph.didExceedMaxLines }
        val bounds: Rect = node.boundsInRoot
        val offScreen = bounds.left < -1f || bounds.right > rootWidth + 1f
        if (cutOff || offScreen) offenders += text
    }
    return offenders
}

/**
 * Scrolls the vertical list on screen until the node with [text] is composed and visible. Lazy lists
 * only compose what is on screen, so [clippedTexts] can only judge what has been scrolled into view.
 */
fun ComposeContentTestRule.scrollListTo(text: String) {
    onNode(
        androidx.compose.ui.test.hasScrollToNodeAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
    ).performScrollToNode(androidx.compose.ui.test.hasText(text))
}
