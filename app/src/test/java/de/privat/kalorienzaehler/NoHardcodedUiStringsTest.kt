package de.privat.kalorienzaehler

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All user-facing text must come from `strings.xml` (PROMPT.md §2.3). Android lint's
 * `HardcodedText` only covers XML layouts, so this test scans the Compose sources for
 * string literals passed to text-bearing parameters.
 */
class NoHardcodedUiStringsTest {

    private val sourceRoot = File("src/main/java")

    // Text("…"), contentDescription = "…", label = "…", etc. with a literal containing a letter.
    private val hardcoded = Regex(
        """(\bText\(\s*|\b(text|title|label|message|placeholder|contentDescription|supportingText|actionLabel)\s*=\s*)"[^"]*\p{L}""",
    )

    @Test
    fun composeSourcesUseStringResources() {
        assertTrue("source root not found: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (hardcoded.containsMatchIn(line)) "${file.relativeTo(sourceRoot)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue("Hardcoded UI strings found (move them to strings.xml):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun detectorFindsTypicalViolations() {
        listOf(
            """Text("Heute")""",
            """Text(text = "Gewicht")""",
            """Icon(painter, contentDescription = "Zurück")""",
            """TopAppBar(title = "Profil")""",
        ).forEach { assertTrue("not detected: $it", hardcoded.containsMatchIn(it)) }
        listOf(
            """Text(stringResource(R.string.nav_today))""",
            """Icon(painter, contentDescription = null)""",
            """Modifier.testTag("diary-list")""",
        ).forEach { assertTrue("false positive: $it", !hardcoded.containsMatchIn(it)) }
    }
}
